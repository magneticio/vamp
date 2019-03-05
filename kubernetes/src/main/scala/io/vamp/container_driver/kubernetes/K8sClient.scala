package io.vamp.container_driver.kubernetes

import java.io._
import java.security.cert.{Certificate, CertificateFactory}
import java.security.{KeyStore, SecureRandom}
import java.util
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger
import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.{ApisApi, BatchV1Api, CoreV1Api, ExtensionsV1beta1Api}
import io.vamp.common.Namespace
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.io.Source
import scala.util.Try

private case class SharedK8sClient(client: K8sClient, counter: Int)

object K8sClient {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val clients = new mutable.HashMap[K8sClientConfig, SharedK8sClient]()

  def acquire(config: K8sClientConfig)(implicit namespace: Namespace, system: ActorSystem): K8sClient = synchronized {
    logger.info(s"acquiring Kubernetes connection: ${config.url}")
    val client = clients.get(config) match {
      case Some(shared) ⇒
        clients.put(config, shared.copy(counter = shared.counter + 1))
        shared.client
      case None ⇒
        logger.info(s"creating new Kubernetes connection: ${config.url}")
        val shared = SharedK8sClient(new K8sClient(config), 1)
        clients.put(config, shared)
        shared.client
    }
    client.acquire()
    client
  }

  def release(config: K8sClientConfig)(implicit namespace: Namespace): Unit = synchronized {
    logger.info(s"releasing Kubernetes connection: ${config.url}")
    clients.get(config) match {
      case Some(shared) if shared.counter == 1 ⇒
        logger.info(s"closing Kubernetes connection: ${config.url}")
        clients.remove(config)
        shared.client.close()
      case Some(shared) ⇒
        shared.client.release()
        clients.put(config, shared.copy(counter = shared.counter - 1))
      case None ⇒
    }
  }
}

class K8sClient(val config: K8sClientConfig)(implicit system: ActorSystem) {

  private val api: ApiClient = {
    val client = new ApiClient()
    client.setBasePath(config.url)
    client.getHttpClient.setReadTimeout(0, TimeUnit.SECONDS)
    val apiKey = if (config.bearer.nonEmpty) config.bearer else Try(Source.fromFile(config.token).mkString).getOrElse("")
    if (apiKey.nonEmpty) client.setApiKey(s"Bearer $apiKey")
    if (config.username.nonEmpty) client.setUsername(config.username)
    if (config.password.nonEmpty) client.setPassword(config.password)
    if (config.serverCaCert.nonEmpty) client.setSslCaCert(new FileInputStream(config.serverCaCert))
    if (config.clientCert.nonEmpty && config.privateKey.nonEmpty) {
      // client.getHttpClient
      setCert(client, config.privateKey, config.clientCert)

    }
    client.setVerifyingSsl(config.tlsCheck)
  }

  /**
    * Pem keys as private key and server certificates used as input in many applications
    * but akka requires PKCS12 type keys for https, so this method is needed for conversion
    * TODO: check keystore if this method is not actually needed.
    * @param keyString private-key
    * @param cerString server-certificate
    * @param password password for keystore default is change me
    * @return PKCS12 file as byte array
    */
  def convertPEMToPKCS12(keyString: String, cerString: String, password: String): Array[Byte] = { // Get the private key
    var reader = new StringReader(keyString)
    var pem = new PEMParser(reader)
    val pemKeyPair = pem.readObject.asInstanceOf[PEMKeyPair]
    val provider = new BouncyCastleProvider()
    val jcaPEMKeyConverter = new JcaPEMKeyConverter().setProvider(provider)
    val keyPair = jcaPEMKeyConverter.getKeyPair(pemKeyPair)
    val key = keyPair.getPrivate
    pem.close()
    reader.close()
    // Get the certificate
    reader = new StringReader(cerString)
    pem = new PEMParser(reader)
    val certHolder = pem.readObject.asInstanceOf[X509CertificateHolder]
    val X509Certificate = new JcaX509CertificateConverter().setProvider(provider).getCertificate(certHolder)
    pem.close()
    reader.close()
    // Put them into a PKCS12 keystore and write it to a byte[]
    val bos = new ByteArrayOutputStream()
    val ks: KeyStore = KeyStore.getInstance("PKCS12")
    ks.load(null)
    val certs = new Array[java.security.cert.Certificate](1)
    certs(0) = X509Certificate
    ks.setKeyEntry("alias", key.asInstanceOf[java.security.Key], password.toCharArray, certs )
    ks.store(bos, password.toCharArray)
    bos.close
    bos.toByteArray
  }


  private def setCert(apiClient: ApiClient, keyfilepath: String, certfilepath: String) : Unit = {

    val password = "change me" // default java password
    val keyfileAsString = scala.io.Source.fromFile(keyfilepath).mkString
    val certfileAsString = scala.io.Source.fromFile(certfilepath).mkString

    val pkcs12certFileAsByteArray = convertPEMToPKCS12(keyfileAsString, certfileAsString, password)

    val keyInput = new ByteArrayInputStream(pkcs12certFileAsByteArray)
    import java.security.KeyStore
    // Testing change me instead of null val password: Array[Char] = null
    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val keyStore: KeyStore = KeyStore.getInstance("PKCS12")

    keyStore.load(keyInput, password.toCharArray)
    keyInput.close()
    keyManagerFactory.init(keyStore, password.toCharArray)
    apiClient.setKeyManagers(keyManagerFactory.getKeyManagers)
  }

  val watch = new K8sWatch(this)

  val caches = new mutable.HashSet[K8sCache]()

  lazy val apisApi: ApisApi = new ApisApi(api)

  lazy val coreV1Api: CoreV1Api = new CoreV1Api(api)

  lazy val batchV1Api: BatchV1Api = new BatchV1Api(api)

  lazy val extensionsV1beta1Api: ExtensionsV1beta1Api = new ExtensionsV1beta1Api(api)

  def cache(implicit namespace: Namespace): K8sCache = caches.find(_.namespace.name == namespace.name).get

  def acquire()(implicit namespace: Namespace): Unit = {
    if (!caches.exists(_.namespace.name == namespace.name)) caches.add(new K8sCache(K8sCacheConfig(), namespace))
  }

  def release()(implicit namespace: Namespace): Unit = caches.find(_.namespace.name == namespace.name).foreach(_.close())

  def close(): Unit = {
    watch.close()
    caches.foreach(_.close())
  }
}

package io.vamp.pulse

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, StringReader }
import java.security.{ KeyStore, SecureRandom }

import akka.actor.Actor
import akka.http.scaladsl.{ ConnectionContext, HttpsConnectionContext }
import io.nats.client.{ Nats, Options }
import io.nats.streaming.{ AckHandler, StreamingConnection, StreamingConnectionFactory }
import io.vamp.common.akka.IoC
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.common.{ ClassMapper, Config, ConfigMagnet }
import io.vamp.model.event._
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.Percolator.{ GetPercolator, RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.notification._
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization.write
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.concurrent.Future
import scala.util.{ Random, Try }
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, StringReader }
import java.security.{ KeyStore, SecureRandom }

import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.{ PEMKeyPair, PEMParser }
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

class NatsPublisherPulseActorMapper extends ClassMapper {
  val name = "natspublisher"
  val clazz: Class[_] = classOf[NatsPublisherPulseActor]
}

object NatsPublisherPulseActor {

  val config: String = PulseActor.config

  val subjectPrefixLayout = "${namespace}"

  val natsUrl: ConfigMagnet[String] = Config.string(s"$config.nats.url")
  val clusterId: ConfigMagnet[String] = Config.string(s"$config.nats.cluster-id")
  val clientId: ConfigMagnet[String] = Config.string(s"$config.nats.client-id")
  val token: ConfigMagnet[String] = Config.string(s"$config.nats.token")
  val clientCert: ConfigMagnet[String] = Config.string(s"$config.nats.client-cert")
  val clientKey: ConfigMagnet[String] = Config.string(s"$config.nats.client-key")
  val username: ConfigMagnet[String] = Config.string(s"$config.nats.username")
  val password: ConfigMagnet[String] = Config.string(s"$config.nats.password")

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
    ks.setKeyEntry("alias", key.asInstanceOf[java.security.Key], password.toCharArray, certs)
    ks.store(bos, password.toCharArray)
    bos.close
    bos.toByteArray
  }

  /**
   * Creates and returns required SSL context using configuration
   * @return Ssl context
   */
  def getSslContext(keyString: String, cerString: String): SSLContext = {

    // This is dockerized so default password is ok
    // This password is for accessing the local keystore, default value is change me.
    // It is designed for personal computers.
    val password: Array[Char] = "change me".toCharArray // do not store passwords in code, read them from somewhere safe!
    val p12 = convertPEMToPKCS12(keyString, cerString, "change me")
    val keystore = new ByteArrayInputStream(p12)
    val ks: KeyStore = KeyStore.getInstance("PKCS12")

    require(keystore != null, "Keystore required!")
    ks.load(keystore, password)
    val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)
    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(ks)
    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    sslContext
  }

}

/**
 * NATS Pulse Actor pushes messages to NATS and also forwards other types of messages to Elasticsearch
 *
 * If you are here since messages are slow check this:
 * https://github.com/nats-io/java-nats-streaming#linux-platform-note
 */
class NatsPublisherPulseActor extends NamespaceValueResolver with PulseActor with PulseActorPublisher {

  import PulseActor._

  /*
  This was actually a generated id for Elasticsearch index names
  To be compatible with Elasticsearch, the same structure is used with a fixed layout
   */
  lazy val subjectPrefixL: String = resolveWithNamespace(NatsPublisherPulseActor.subjectPrefixLayout, lookup = true)

  lazy val randomId = Random.alphanumeric.take(5).mkString("")

  lazy val clusterId = NatsPublisherPulseActor.clusterId()
  lazy val clientId = s"${NatsPublisherPulseActor.clientId()}_${namespace.name}_$randomId"
  lazy val token = NatsPublisherPulseActor.token()
  lazy val username = NatsPublisherPulseActor.username()
  lazy val password = NatsPublisherPulseActor.password()
  lazy val clientCert = NatsPublisherPulseActor.clientCert()
  lazy val clientKey = NatsPublisherPulseActor.clientKey()

  lazy val clientKeyContent: String = scala.io.Source.fromFile(clientKey).mkString
  lazy val clientCertContent: String = scala.io.Source.fromFile(clientCert).mkString

  lazy val natsUrl = NatsPublisherPulseActor.natsUrl()
  lazy val cf = {
    val scf = new StreamingConnectionFactory(clusterId, clientId)
    val ctx = NatsPublisherPulseActor.getSslContext(clientKeyContent, clientCertContent)
    scf.setNatsUrl(natsUrl)
    val o = new Options.Builder()
      .server(natsUrl)
      .userInfo(username, password)
      .token(token)
      .sslContext(ctx)
      .maxReconnects(-1)
      .build
    val nc = Nats.connect(o)
    scf.setNatsConnection(nc)
    scf
  }

  /**
   * Starts a logical connection to the NATS cluster
   * Documentation: https://github.com/nats-io/java-nats-streaming#basic-usage
   */
  lazy val sc: StreamingConnection = cf.createConnection

  // The ack handler will be invoked when a publish acknowledgement is received
  // This is a def, not a val due to possible concurrency issues
  // ackHandler is only used in asynchronous case
  def ackHandler(subject: String, message: String, count: Int = 5): AckHandler = new AckHandler() {
    override def onAck(guid: String, err: Exception): Unit = {
      if (err != null) {
        logger.error("Error publishing msg id %s: %s %d".format(guid, err.getMessage, count))
        if (count > 0)
          sc.publish(subject, message.getBytes, ackHandler(subject, message, count - 1))
      }
      else {
        logger.info("Received ack for msg id %s ".format(guid))
      }
    }
  }

  override def postStop(): Unit = {
    Try(sc.close())
  }

  def receive: Actor.Receive = {

    case InfoRequest ⇒ reply(info)

    case StatsRequest ⇒ IoC.actorFor[PulseActorSupport].forward(StatsRequest)

    case Publish(event, publishEventValue) ⇒ reply((validateEvent andThen publish(publishEventValue) andThen broadcast(publishEventValue))(Event.expandTags(event)), classOf[EventIndexError])

    case Query(envelope) ⇒ IoC.actorFor[PulseActorSupport].forward(Query(envelope))

    case GetPercolator(name) ⇒ reply(Future.successful(getPercolator(name)))

    case RegisterPercolator(name, tags, kind, message) ⇒ registerPercolator(name, tags, kind, message)

    case UnregisterPercolator(name) ⇒ unregisterPercolator(name)

    case any ⇒ unsupported(UnsupportedPulseRequest(any))
  }

  private def info = Future { Map[String, Any]("type" → "nats", "nats" → clusterId) }

  private def publish(publishEventValue: Boolean)(event: Event): Future[Any] = Future {

    implicit val formats: Formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))

    val attachment = (publishEventValue, event.value) match {
      case (true, str: String) ⇒ Map(typeName → str)
      case (true, any)         ⇒ Map("value" → write(any)(DefaultFormats), typeName → (if (typeName == Event.defaultType) "" else any))
      case (false, _)          ⇒ Map("value" → "")
    }

    val data = Extraction.decompose(if (publishEventValue) event else event.copy(value = None)) merge Extraction.decompose(attachment)

    val subject = {
      val prefix = s"$subjectPrefixL-${event.`type`}"
      val postfix = event.`type` match {
        case Event.defaultType if event.tags.contains("gateways") ⇒ "-gateways"
        case _ ⇒ ""
      }
      s"$prefix$postfix"
    }.replace(" ", "_")
    val message = bodyAsString(data).getOrElse("")

    logger.info(s"Pulse publish an event with subject $subject and message: $message")
    // This is a synchronous (blocking) call
    // This can throw an exception currently it is unhandled so actor will be restarted with the same message
    // sc.publish(subject, message.getBytes)
    // logger.info(s" Pulse published an event with subject $subject")

    // Testing: following method is asynchronous, try asynchronous connections later if feasible
    val guid = sc.publish(subject, message.getBytes, ackHandler(subject, message))
    event.copy(id = Option(guid))
  }

  private def broadcast(publishEventValue: Boolean): Future[Any] ⇒ Future[Any] = _.map {
    case event: Event ⇒ percolate(publishEventValue)(event)
    case other        ⇒ other
  }

  // this is copied from httpClient
  private def bodyAsString(body: Any)(implicit formats: Formats): Option[String] = body match {
    case string: String                            ⇒ Some(string)
    case Some(string: String)                      ⇒ Some(string)
    case Some(some: AnyRef)                        ⇒ Some(write(some))
    case any: AnyRef if any != null && any != None ⇒ Some(write(any))
    case any if any != null && any != None         ⇒ Some(any.toString)
    case _                                         ⇒ None
  }
}
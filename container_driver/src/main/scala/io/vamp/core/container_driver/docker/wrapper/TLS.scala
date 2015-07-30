package io.vamp.core.container_driver.docker.wrapper

import java.io.{BufferedReader, FileInputStream, FileReader}
import java.security.cert.{Certificate, CertificateFactory}
import java.security.{KeyPair, KeyStore, SecureRandom, Security}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory, X509TrustManager}

import com.ning.http.client.AsyncHttpClientConfig.Builder
import com.ning.http.client.SSLEngineFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMReader

/** Encapsulates tls configuration for http connections required for tcp security requirements
  * to encure client communication is certified.
  * see also jvm system property -Djavax.net.debug=all for debugging
  */
case class TLS(
                keyPath: String, certPath: String, caPath: Option[String]) {

  // required for bouncy castle open ssl pem reader below
  Security.addProvider(new BouncyCastleProvider)

  def certify(builder: Builder): Builder = {
    def certificate(path: String): Certificate = {
      val certStm = new FileInputStream(path)
      try CertificateFactory.getInstance("X.509").generateCertificate(certStm)
      finally certStm.close()
    }

    def withStore[T](f: KeyStore => T): KeyStore = {
      val store = KeyStore.getInstance(KeyStore.getDefaultType)
      f(store)
      store
    }

    def keyStore = {
      // using bouncycastle b/c the provided key may not be in pkcs8 format (boot2dockers keys are not)
      // bouncycastle's PEM reader seems a bit more robust
      val key = new PEMReader(
        new BufferedReader(new FileReader(keyPath)))
        .readObject().asInstanceOf[KeyPair].getPrivate
      withStore { store =>
        store.load(null, null)
        store.setKeyEntry(
          "key", key, "".toCharArray, Array(certificate(certPath)))
      }
    }

    def trustStore(caPath: String) = withStore { store =>
      store.load(null, null)
      store.setCertificateEntry("cacert", certificate(caPath))
    }

    def trustManager(capath: String) = {
      val fact = TrustManagerFactory.getInstance("SunX509", "SunJSSE")
      fact synchronized {
        fact.init(trustStore(capath))
        fact.getTrustManagers.find(man => man.isInstanceOf[X509TrustManager])
      }
    }

    def keyManagers = {
      val algo = Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse("SunX509")
      val kmf = KeyManagerFactory.getInstance(algo)
      kmf.init(keyStore, "".toCharArray)
      kmf.getKeyManagers
    }

    val ctx = SSLContext.getInstance("TLSv1")
    val trust = for {
      ca <- caPath
      trust <- trustManager(ca)
    } yield trust

    ctx.init(keyManagers, trust.map(Array(_)).orNull, new SecureRandom)

    // protocols
    val sslParams = ctx.getDefaultSSLParameters
    val protocols = Array("TLSv1")
    sslParams.setProtocols(protocols)

    builder.setSSLContext(ctx).setSSLEngineFactory(new SSLEngineFactory() {
      def newSSLEngine(): SSLEngine = {
        val engine = ctx.createSSLEngine()
        engine.setSSLParameters(ctx.getDefaultSSLParameters)
        engine.setEnabledProtocols(protocols)
        engine.setUseClientMode(true)
        engine
      }
    })
  }
}

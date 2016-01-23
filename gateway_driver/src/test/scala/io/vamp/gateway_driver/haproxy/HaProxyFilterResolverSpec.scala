package io.vamp.gateway_driver.haproxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class HaProxyFilterResolverSpec extends FlatSpec with Matchers with HaProxyAclResolver {

  //  "FilterConditionResolver" should "resolve single" in {
  //    resolve("User-Agent has Firefox" :: Nil) shouldBe None
  //    resolve("User-Agent != Firefox or 1" :: Nil) shouldBe None
  //    resolve("User-Agent == Firefox and 1" :: Nil) shouldBe None
  //    resolve("User-Agent != Firefox or User-Agent = Firefox" :: Nil) shouldBe None
  //    resolve("User-Agent != Firefox && User-Agent = Firefox" :: Nil) shouldBe None
  //    resolve("user-agent == Firefox && has cookie vamp" :: Nil) shouldBe None
  //  }

  it should "resolve multiple" in {
    //    resolve("User-Agent != Firefox" :: "User-Agent == Firefox" :: Nil) shouldBe None
    //    resolve("user-agent == Firefox" :: "has cookie vamp" :: Nil) shouldBe None

    resolve("user-agent == Firefox or user-agent != Chrome" :: "has cookie vamp" :: Nil) shouldBe Some {
      HaProxyAcls(List(
        Acl("81b5022a1c5966ab", "hdr_sub(user-agent) Chrome"), Acl("d2c606178591676a", "cook(vamp) -m found"), Acl("af31629d4c4c8e71", "hdr_sub(user-agent) Firefox")
      ),
        Some("!81b5022a1c5966ab d2c606178591676a or af31629d4c4c8e71 d2c606178591676a")
      )
    }
  }
}
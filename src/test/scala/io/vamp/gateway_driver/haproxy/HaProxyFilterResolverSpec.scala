package io.vamp.gateway_driver.haproxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class HaProxyFilterResolverSpec extends FlatSpec with Matchers with HaProxyAclResolver {

  "FilterConditionResolver" should "resolve single" in {
    resolve("User-Agent is Firefox" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("af31629d4c4c8e71", "hdr_sub(user-agent) Firefox")), Some("af31629d4c4c8e71"))
    }

    resolve("host == localhost" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("58966872db928351", "hdr_str(host) localhost")), Some("58966872db928351"))
    }

    resolve("contains cookie vamp" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("d2c606178591676a", "cook(vamp) -m found")), Some("d2c606178591676a"))
    }

    resolve("has header page" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("9ff7c12a5a399997", "hdr_cnt(page) gt 0")), Some("9ff7c12a5a399997"))
    }

    resolve("cookie vamp has 123" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("f243b455cb6f05a8", "cook_sub(vamp) 123")), Some("f243b455cb6f05a8"))
    }

    resolve("header page contains 1" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("d87230f59992cc95", "hdr_sub(page) 1")), Some("d87230f59992cc95"))
    }

    resolve("misses cookie vamp" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("ab767be5e83a8746", "cook_cnt(vamp) eq 0")), Some("ab767be5e83a8746"))
    }

    resolve("misses header page" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("615b1f1c2f9c25c3", "hdr_cnt(page) eq 0")), Some("615b1f1c2f9c25c3"))
    }

    resolve("hdr_sub(user-agent) Android" :: Nil) shouldBe Some {
      HaProxyAcls(List(Acl("29c278b48a0ff033", "hdr_sub(user-agent) Android")), Some("29c278b48a0ff033"))
    }

    resolve("hdr_sub(user-agent) Firefox" :: "hdr_sub(user-agent) Chrome" :: Nil) shouldBe Some {
      HaProxyAcls(List(
        Acl("af31629d4c4c8e71", "hdr_sub(user-agent) Firefox"), Acl("81b5022a1c5966ab", "hdr_sub(user-agent) Chrome")
      ),
        Some("af31629d4c4c8e71 81b5022a1c5966ab")
      )
    }
  }

  it should "resolve multiple" in {
    resolve("user-agent == Firefox or user-agent != Chrome" :: "has cookie vamp" :: Nil) shouldBe Some {
      HaProxyAcls(List(
        Acl("81b5022a1c5966ab", "hdr_sub(user-agent) Chrome"), Acl("d2c606178591676a", "cook(vamp) -m found"), Acl("af31629d4c4c8e71", "hdr_sub(user-agent) Firefox")
      ),
        Some("!81b5022a1c5966ab d2c606178591676a or af31629d4c4c8e71 d2c606178591676a")
      )
    }
  }
}

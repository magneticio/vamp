package io.vamp.gateway_driver.haproxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class HaProxyAclResolverSpec extends FlatSpec with Matchers with HaProxyAclResolver {

  "ConditionDefinitionResolver" should "resolve single" in {

    resolve("User-Agent is Firefox") shouldBe Some {
      HaProxyAcls(List(Acl("7b796e5d25d870c6", "req.fhdr(User-Agent) -m sub 'Firefox'")), Some("7b796e5d25d870c6"))
    }

    resolve("User-Agent is Safari") shouldBe Some {
      HaProxyAcls(List(Acl("d5f16211db6f5105", "req.fhdr(User-Agent) -m sub 'Safari'"), Acl("2d9aa9e2c5ea6536", "req.fhdr(User-Agent) -m sub 'Version'")), Some("d5f16211db6f5105 2d9aa9e2c5ea6536"))
    }

    resolve("host == localhost") shouldBe Some {
      HaProxyAcls(List(Acl("58966872db928351", "hdr_str(host) localhost")), Some("58966872db928351"))
    }

    resolve("contains cookie vamp") shouldBe Some {
      HaProxyAcls(List(Acl("d2c606178591676a", "cook(vamp) -m found")), Some("d2c606178591676a"))
    }

    resolve("has header page") shouldBe Some {
      HaProxyAcls(List(Acl("9ff7c12a5a399997", "hdr_cnt(page) gt 0")), Some("9ff7c12a5a399997"))
    }

    resolve("cookie vamp has 123") shouldBe Some {
      HaProxyAcls(List(Acl("f243b455cb6f05a8", "cook_sub(vamp) 123")), Some("f243b455cb6f05a8"))
    }

    resolve("header page contains 1") shouldBe Some {
      HaProxyAcls(List(Acl("2640a60ce3da6491", "req.fhdr(page) -m sub '1'")), Some("2640a60ce3da6491"))
    }

    resolve("misses cookie vamp") shouldBe Some {
      HaProxyAcls(List(Acl("ab767be5e83a8746", "cook_cnt(vamp) eq 0")), Some("ab767be5e83a8746"))
    }

    resolve("misses header page") shouldBe Some {
      HaProxyAcls(List(Acl("615b1f1c2f9c25c3", "hdr_cnt(page) eq 0")), Some("615b1f1c2f9c25c3"))
    }

    resolve("hdr_sub(user-agent) Android") shouldBe Some {
      HaProxyAcls(List(Acl("29c278b48a0ff033", "hdr_sub(user-agent) Android")), Some("29c278b48a0ff033"))
    }

    resolve("< hdr_sub(user-agent) Android >") shouldBe Some {
      HaProxyAcls(List(Acl("29c278b48a0ff033", "hdr_sub(user-agent) Android")), Some("29c278b48a0ff033"))
    }
  }

  it should "resolve multiple" in {

    resolve("<hdr_sub(user-agent) Android> or <hdr_sub(user-agent) Chrome>") shouldBe Some {
      HaProxyAcls(
        List(
          Acl("29c278b48a0ff033", "hdr_sub(user-agent) Android"), Acl("81b5022a1c5966ab", "hdr_sub(user-agent) Chrome")
        ),
        Some("29c278b48a0ff033 or 81b5022a1c5966ab")
      )
    }

    resolve("not <hdr_sub(user-agent) Android> and User-Agent != Chrome") shouldBe Some {
      HaProxyAcls(
        List(
          Acl("29c278b48a0ff033", "hdr_sub(user-agent) Android"), Acl("de0de9d22824714a", "req.fhdr(User-Agent) -m sub 'Chrome'")
        ),
        Some("!29c278b48a0ff033 !de0de9d22824714a")
      )
    }

    resolve("not <hdr_sub(user-agent) Android> or User-Agent != Chrome") shouldBe Some {
      HaProxyAcls(
        List(
          Acl("de0de9d22824714a", "req.fhdr(User-Agent) -m sub 'Chrome'"), Acl("29c278b48a0ff033", "hdr_sub(user-agent) Android")
        ),
        Some("!de0de9d22824714a or !29c278b48a0ff033")
      )
    }

    resolve("(User-Agent = Chrome OR User-Agent = Firefox) AND has cookie vamp") shouldBe Some {
      HaProxyAcls(
        List(
          Acl("de0de9d22824714a", "req.fhdr(User-Agent) -m sub 'Chrome'"), Acl("d2c606178591676a", "cook(vamp) -m found"), Acl("7b796e5d25d870c6", "req.fhdr(User-Agent) -m sub 'Firefox'")
        ),
        Some("de0de9d22824714a d2c606178591676a or 7b796e5d25d870c6 d2c606178591676a")
      )
    }

    resolve("User-Agent == \"Mozilla/5.0 Macintosh\"") shouldBe Some {
      HaProxyAcls(List(Acl("3581fea35ba6e992", "req.fhdr(User-Agent) -m sub 'Mozilla/5.0 Macintosh'")), Some("3581fea35ba6e992"))
    }

    resolve("User-Agent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/602.2.14 (KHTML, like Gecko) Version/10.0.1 Safari/602.2.14'") shouldBe Some {
      HaProxyAcls(List(Acl("1fc41db2449a9c62", "req.fhdr(User-Agent) -m sub 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/602.2.14 (KHTML, like Gecko) Version/10.0.1 Safari/602.2.14'")), Some("1fc41db2449a9c62"))
    }
  }
}

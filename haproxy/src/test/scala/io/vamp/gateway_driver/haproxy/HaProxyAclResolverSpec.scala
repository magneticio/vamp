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
      HaProxyAcls(List(Acl("63d10221023637c8", "req.hdr(host) localhost")), Some("63d10221023637c8"))
    }

    resolve("contains cookie vamp") shouldBe Some {
      HaProxyAcls(List(Acl("c506a28623f9f83d", "req.cook(vamp) -m found")), Some("c506a28623f9f83d"))
    }

    resolve("has header page") shouldBe Some {
      HaProxyAcls(List(Acl("71165113ade1e335", "req.hdr_cnt(page) gt 0")), Some("71165113ade1e335"))
    }

    resolve("cookie vamp has 123") shouldBe Some {
      HaProxyAcls(List(Acl("60a2a71edc375d21", "req.cook_sub(vamp) 123")), Some("60a2a71edc375d21"))
    }

    resolve("header page contains 1") shouldBe Some {
      HaProxyAcls(List(Acl("2640a60ce3da6491", "req.fhdr(page) -m sub '1'")), Some("2640a60ce3da6491"))
    }

    resolve("misses cookie vamp") shouldBe Some {
      HaProxyAcls(List(Acl("85d714c5ede2f624", "req.cook_cnt(vamp) eq 0")), Some("85d714c5ede2f624"))
    }

    resolve("misses header page") shouldBe Some {
      HaProxyAcls(List(Acl("75f52ab77f4adcfc", "req.hdr_cnt(page) eq 0")), Some("75f52ab77f4adcfc"))
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
          Acl("de0de9d22824714a", "req.fhdr(User-Agent) -m sub 'Chrome'"), Acl("c506a28623f9f83d", "req.cook(vamp) -m found"), Acl("7b796e5d25d870c6", "req.fhdr(User-Agent) -m sub 'Firefox'")
        ),
        Some("de0de9d22824714a c506a28623f9f83d or 7b796e5d25d870c6 c506a28623f9f83d")
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

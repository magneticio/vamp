package io.vamp.model.parser

trait FilterConditionResolver extends FilterConditionParser with BooleanFlatter {

  def resolve(input: String): AstNode = flatten(parse(input))

  //  case userAgent(n, c)        ⇒ Condition(s"hdr_sub(user-agent) ${c.trim}", n == "!") :: Nil
  //  case host(n, c)             ⇒ Condition(s"hdr_str(host) ${c.trim}", n == "!") :: Nil
  //  case cookieContains(c1, c2) ⇒ Condition(s"cook_sub(${c1.trim}) ${c2.trim}") :: Nil
  //  case hasCookie(c)           ⇒ Condition(s"cook(${c.trim}) -m found") :: Nil
  //  case missesCookie(c)        ⇒ Condition(s"cook_cnt(${c.trim}) eq 0") :: Nil
  //  case headerContains(h, c)   ⇒ Condition(s"hdr_sub(${h.trim}) ${c.trim}") :: Nil
  //  case hasHeader(h)           ⇒ Condition(s"hdr_cnt(${h.trim}) gt 0") :: Nil
  //  case missesHeader(h)        ⇒ Condition(s"hdr_cnt(${h.trim}) eq 0") :: Nil
}

package io.vamp.gateway_driver.haproxy

import io.vamp.model.parser._

import scala.util.Try

case class AclNode(value: String) extends Operand

case class HaProxyAcls(acls: List[Acl], condition: Option[String])

trait HaProxyAclResolver extends FilterConditionParser with BooleanFlatter {

  def resolve(conditions: List[String]): Option[HaProxyAcls] = conditions match {
    case Nil ⇒ None
    case _ ⇒

      val root = conditions.map { condition ⇒ Try(parse(condition)).getOrElse(Value(condition)) } reduce { (op1, op2) ⇒ And(op1, op2) }

      flatten(root) match {
        case False ⇒ None
        case True  ⇒ Option(HaProxyAcls(Nil, None))
        case node  ⇒ acls(node) match { case acls ⇒ Option(HaProxyAcls(operands(acls).distinct, Option(condition(acls)))) }
      }
  }

  private def acls(node: AstNode): AstNode = node match {
    case Host(value)                 ⇒ AclNode(s"hdr_str(host) $value")
    case Cookie(value)               ⇒ AclNode(s"cook($value) -m found")
    case Header(value)               ⇒ AclNode(s"hdr_cnt($value) gt 0")
    case UserAgent(value)            ⇒ AclNode(s"hdr_sub(user-agent) $value")
    case CookieContains(name, value) ⇒ AclNode(s"cook_sub($name) $value")
    case HeaderContains(name, value) ⇒ AclNode(s"hdr_sub($name) $value")
    case Negation(Cookie(value))     ⇒ AclNode(s"cook_cnt($value) eq 0")
    case Negation(Header(value))     ⇒ AclNode(s"hdr_cnt($value) eq 0")
    case Value(value)                ⇒ AclNode(value)
    case Negation(op)                ⇒ Negation(acls(op))
    case Or(op1, op2)                ⇒ Or(acls(op1), acls(op2))
    case And(op1, op2)               ⇒ And(acls(op1), acls(op2))
    case other                       ⇒ unsupported(other)
  }

  private def operands(node: AstNode): List[Acl] = node match {
    case AclNode(value)          ⇒ Acl(value) :: Nil
    case Negation(operand)       ⇒ operands(operand)
    case Or(operand1, operand2)  ⇒ operands(operand1) ++ operands(operand2)
    case And(operand1, operand2) ⇒ operands(operand1) ++ operands(operand2)
    case other                   ⇒ unsupported(other)
  }

  private def condition(node: AstNode): String = node match {
    case AclNode(value)           ⇒ Acl(value).name
    case Negation(AclNode(value)) ⇒ s"!${Acl(value).name}"
    case And(operand1, operand2)  ⇒ s"${condition(operand1)} ${condition(operand2)}"
    case Or(operand1, operand2)   ⇒ s"${condition(operand1)} or ${condition(operand2)}"
    case other                    ⇒ unsupported(other)
  }

  private def unsupported(node: AstNode) = throw new RuntimeException(s"unsupported ACL node: $node")
}

package io.vamp.gateway_driver.haproxy

import io.vamp.model.parser._
import org.yaml.snakeyaml.Yaml

import scala.io.Source
import scala.util.Try
import scala.collection.JavaConverters._

case class AclNode(value: String) extends Operand

case class HaProxyAcls(acls: List[Acl], condition: Option[String])

trait HaProxyAclResolver extends ConditionDefinitionParser with BooleanFlatter {

  private val userAgents = {
    val reader = Source.fromURL(getClass.getResource("/io/vamp/gateway_driver/haproxy/user-agents.yml")).bufferedReader()
    try {
      new Yaml().load(reader).asInstanceOf[java.util.Map[String, String]].asScala.map { case (k, v) ⇒ k.toUpperCase → v }
    } finally {
      reader.close()
    }
  }

  def resolve(value: String): Option[HaProxyAcls] = {
    (load andThen expand andThen flatten)(value) match {
      case False ⇒ None
      case True  ⇒ Option(HaProxyAcls(Nil, None))
      case node  ⇒ acls(node) match { case acls ⇒ Option(HaProxyAcls(operands(acls).distinct, Option(condition(acls)))) }
    }
  }

  private def load: String ⇒ AstNode = value ⇒ Try(parse(value)).getOrElse(Value(value))

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

  private def expand: AstNode ⇒ AstNode = {
    case UserAgent(value)        ⇒ expandUserAgent(value)
    case Negation(operand)       ⇒ Negation(expand(operand))
    case And(operand1, operand2) ⇒ And(expand(operand1), expand(operand2))
    case Or(operand1, operand2)  ⇒ Or(expand(operand1), expand(operand2))
    case other                   ⇒ other
  }

  private def expandUserAgent: String ⇒ AstNode = { value ⇒
    userAgents.get(value.toUpperCase) match {
      case None             ⇒ UserAgent(value)
      case Some(definition) ⇒ load(definition)
    }
  }

  private def unsupported(node: AstNode) = throw new RuntimeException(s"unsupported ACL node: $node")
}

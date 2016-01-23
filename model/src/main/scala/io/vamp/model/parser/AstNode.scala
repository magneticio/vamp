package io.vamp.model.parser

sealed trait AstNode

trait Operand extends AstNode

sealed trait Operation extends AstNode

case class Or(operand1: AstNode, operand2: AstNode) extends Operation {
  override def equals(that: Any): Boolean = that match {
    case Or(op1, op2) ⇒ (op1 == operand1 && op2 == operand2) || (op1 == operand2 && op2 == operand1)
    case _            ⇒ super.equals(that)
  }
}

case class And(operand1: AstNode, operand2: AstNode) extends Operation {
  override def equals(that: Any): Boolean = that match {
    case And(op1, op2) ⇒ (op1 == operand1 && op2 == operand2) || (op1 == operand2 && op2 == operand1)
    case _             ⇒ super.equals(that)
  }
}

case class Negation(operand: AstNode) extends Operation

case class Value(value: String) extends Operand

package io.vamp.model.parser

sealed trait AstNode

trait Operand extends AstNode

trait Operation extends AstNode

case class Or(operand1: AstNode, operand2: AstNode) extends Operation

case class And(operand1: AstNode, operand2: AstNode) extends Operation

case class Negation(operand: AstNode) extends Operation

case class Value(value: String) extends Operand

//case class UserAgent(value: String) extends AstNode
//
//case class NativeAcl(value: String) extends AstNode

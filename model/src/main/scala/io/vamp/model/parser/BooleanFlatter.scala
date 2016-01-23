package io.vamp.model.parser

import scala.language.postfixOps

private case class ReductionInput(nodes: List[AstNode], value: Long, reduced: Boolean = false)

/**
 * http://stackoverflow.com/questions/13496172/converting-conditions-with-parentheses-to-equivalents-with-no-parentheses
 */
trait BooleanFlatter {

  def flatten(node: AstNode): AstNode = (map andThen reduce)(node)

  private[parser] def map: AstNode ⇒ List[ReductionInput] = { node ⇒
    val ops = operands(node)

    (0L until 1 << ops.size) flatMap { value ⇒
      val map = ops.zipWithIndex.map {
        case (op, index) ⇒ op -> ((value & 1 << index) > 0)
      } toList

      if (calculate(node, map)) ReductionInput(terms(map), value) :: Nil else Nil
    } toList
  }

  private[parser] def reduce: List[ReductionInput] ⇒ AstNode = { terms ⇒

    var previous = terms
    var result = shrink(terms)

    while (previous.size != result.size) {
      previous = result
      result = shrink(terms)
    }

    result map {
      _.nodes.reduce {
        (op1, op2) ⇒ And(op1, op2)
      }
    } reduce {
      (op1, op2) ⇒ Or(op1, op2)
    }
  }

  private def operands(node: AstNode): Set[Operand] = node match {
    case operand: Operand        ⇒ Set(operand)
    case Negation(operand)       ⇒ operands(operand)
    case Or(operand1, operand2)  ⇒ operands(operand1) ++ operands(operand2)
    case And(operand1, operand2) ⇒ operands(operand1) ++ operands(operand2)
  }

  private def calculate(node: AstNode, values: List[(Operand, Boolean)]): Boolean = node match {
    case op: Operand   ⇒ values.find(_._1 == op).get._2
    case Negation(op)  ⇒ !calculate(op, values)
    case Or(op1, op2)  ⇒ calculate(op1, values) || calculate(op2, values)
    case And(op1, op2) ⇒ calculate(op1, values) && calculate(op2, values)
  }

  private def terms(values: List[(Operand, Boolean)]): List[AstNode] = values.map {
    case (op, value) ⇒ if (value) op else Negation(op)
  }

  private def shrink(input: List[ReductionInput]): List[ReductionInput] = input match {
    case head :: Nil  ⇒ if (head.reduced) Nil else head :: Nil
    case head +: tail ⇒ shrink(head, tail)
  }

  private def shrink(node: ReductionInput, input: List[ReductionInput]): List[ReductionInput] = {
    val shrank = collection.mutable.ArrayBuffer.empty[ReductionInput]

    val tail = input.map { term ⇒
      val comparison = term.value ^ node.value
      if (powerOf2(comparison) && term.nodes.size == node.nodes.size) {
        shrank += shrink(node, comparison)
        term.copy(reduced = true)
      } else term
    }

    shrank.toList ++ shrink(tail)
  }

  private def shrink(node: ReductionInput, comparison: Long): ReductionInput = {
    node.copy(nodes = node.nodes.zipWithIndex.flatMap {
      case (n, index) ⇒ if (comparison == (1 << index)) Nil else n :: Nil
    })
  }

  private def powerOf2(x: Long) = (x != 0) && ((x & (x - 1)) == 0)
}

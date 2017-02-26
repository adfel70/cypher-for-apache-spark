package org.opencypher.spark.prototype.ir

import org.opencypher.spark.prototype.ir.block._
import org.opencypher.spark.prototype.ir.global.GlobalsRegistry

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom

final case class QueryModel[E](
  result: ResultBlock[E],
  globals: GlobalsRegistry,
//  bindings: Map[ConstantRef, ConstantBinding],
  blocks: Map[BlockRef, Block[E]]
) {

  def apply(ref: BlockRef): Block[E] = blocks(ref)

  def dependencies(ref: BlockRef): Set[BlockRef] = apply(ref).after

  def allDependencies(ref: BlockRef): Set[BlockRef] =
    allDependencies(dependencies(ref).toList, List.empty, Set(ref)) - ref

  @tailrec
  private def allDependencies(current: List[BlockRef], remaining: List[Set[BlockRef]], deps: Set[BlockRef])
  : Set[BlockRef] = {
    if (current.isEmpty) {
      remaining match {
        case hd :: tl => allDependencies(hd.toList, tl, deps)
        case _ => deps
      }
    } else {
      current match {
        case hd :: _ if deps(hd) =>
          throw new IllegalStateException("Cycle of blocks detected!")

        case hd :: tl =>
          allDependencies(tl, dependencies(hd) +: remaining, deps + hd)

        case _ =>
          deps
      }
    }
  }

  def collect[T, That](f: PartialFunction[(BlockRef, Block[E]), T])(implicit bf: CanBuildFrom[Map[BlockRef, Block[E]], T, That]): That = {
    blocks.collect(f)
  }
}

case class SolvedQueryModel[E](fields: Set[Field], predicates: Set[E]) {
  def solves(block: Block[E]) = {
    block.binds.fields.subsetOf(fields) && block.where.elts.subsetOf(predicates)
  }
  def withField(f: Field) = copy(fields = fields + f)
  def withFields(fs: Field*) = copy(fields = fields ++ fs)
  def withPredicate(pred: E) = copy(predicates = predicates + pred)
  def contains(blocks: Set[Block[E]]): Boolean = blocks.forall(contains)
  def contains(block: Block[E]): Boolean = {
    val binds = block.binds.fields subsetOf fields
    val preds = block.where.elts subsetOf predicates

    binds && preds
  }
}
object SolvedQueryModel {
  def empty[E] = SolvedQueryModel[E](Set.empty, Set.empty)
}
// sealed trait ConstantBinding
// final case class ParameterBinding(name: String)

package com.codecommit.antixml

import monocle.function.{At, Index}
import monocle.{Traversal, _}

import scala.language.{dynamics, higherKinds}
import scalaz.Applicative

object NodeOptics {

  // traversal from an elem to its elem children
  final lazy val eachChild: Traversal[Elem, Elem] = eachChild(None)

  // traversal from an elem to its elem children with a certain label
  final def eachChild(field: Option[String]): Traversal[Elem, Elem] = new Traversal[Elem, Elem] {
    override def modifyF[F[_]](f: Elem => F[Elem])(e: Elem)(implicit F: Applicative[F]): F[Elem] = {

      // lift the f to be Node => F[Node]
      val n2Fn: Node => F[Node] = {
        case e: Elem if field.isEmpty || field.contains(e.name) => F.map(f(e))(identity[Node]) // f and upcast to Node
        case x => F.pure(x) // just leave others as they are
      }

      e.traverse[F](n2Fn)
    }
  }

  // FIXME maybe i can split the "select all the elem children with a given name" and the "select the nth child" parts
  implicit final val at: At[Elem, (String, Int), Option[Elem]] = new At[Elem, (String, Int), Option[Elem]] {
    override def at(i: (String, Int)): Lens[Elem, Option[Elem]] = {

      def eqName(elem: Elem) = elem.name == i._1
      def eqIdx(idx: Int) = idx == i._2

      Lens[Elem, Option[Elem]] { e =>
        e.children.foldLeft((0, None: Option[Elem])) {
          case ((_, Some(found)), _) => (0, Some(found))
          case ((curIdx, None), cur: Elem) if eqName(cur) && eqIdx(curIdx) => (0, Some(cur))
          case ((curIdx, None), cur: Elem) if eqName(cur) => (curIdx + 1, None)
          case ((curIdx, None), _) => (curIdx, None)
        }._2
      } { oe =>
        e =>
          e.copy(children =
            Group.fromSeq(
              e.children.foldLeft((0, false, Seq(): Seq[Node])) {
                case ((_, true, acc), cur) => (0, true, acc.:+(cur))
                case ((curIdx, _, acc), cur: Elem) if eqName(cur) && eqIdx(curIdx) => (0, true, oe.fold(acc)(acc.:+(_)))
                case ((curIdx, _, acc), cur: Elem) if eqName(cur) => (curIdx + 1, false, acc.:+(cur))
                case ((curIdx, _, acc), cur) => (curIdx, false, acc.:+(cur))
              }._3
            )
          )
      }
    }
  }

  implicit final val idx: Index[Elem, (String, Int), Elem] = Index.fromAt[Elem, (String, Int), Elem]
}

final case class XMLTraversalPath(e: Traversal[Elem, Elem]) extends Dynamic {

  import NodeOptics._

  def selectDynamic(lbl: String): XMLTraversalPath = XMLTraversalPath(e.composeTraversal(eachChild(Some(lbl))))

  def applyDynamic(lbl: String)(i: Int): XMLTraversalPath = XMLTraversalPath(e.composeOptional(Index.index((lbl, i))))

  def each = XMLTraversalPath(e.composeTraversal(eachChild))

  def modify(f: Elem => Elem)(input: Elem): Elem = e.modify(f)(input)

  def getAll(input: Elem): List[Elem] = e.getAll(input)

  def headOption(input: Elem): Option[Elem] = e.headOption(input)

  def set(substitute: Elem)(input: Elem): Elem = e.set(substitute)(input)

}

object XMLPath {
  val root: XMLTraversalPath = XMLTraversalPath(Optional.id[Elem].asTraversal)
}

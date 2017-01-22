package com.codecommit.antixml

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

}

final case class XMLTraversalPath(e: Traversal[Elem, Elem]) extends Dynamic {

  import NodeOptics._

  def selectDynamic(label: String): XMLTraversalPath = XMLTraversalPath(e.composeTraversal(eachChild(Some(label))))

  def each = XMLTraversalPath(e.composeTraversal(eachChild))

  def modify(f: Elem => Elem)(input: Elem): Elem = e.modify(f)(input)

  def getAll(input: Elem): List[Elem] = e.getAll(input)

  def set(substitute: Elem)(input: Elem): Elem = e.set(substitute)(input)

}

object XMLPath {
  val root: XMLTraversalPath = XMLTraversalPath(Optional.id[Elem].asTraversal)
}

package com.codecommit.antixml

import monocle.law.discipline.TraversalTests
import org.scalacheck._
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.scalacheck.Parameters

import scalaz.Equal

class XMLPathLawsSpecs extends Specification with ScalaCheck with XMLGenerators {

  "elem 2 elem traversal" should {
    "respect traversal laws" in TraversalTests(NodeOptics.eachChild).all
  }

  implicit val groupNode = arbGroup[Node]
  implicit val groupElem = arbGroup[Elem]

  implicit val arbFnGroupNode: Arbitrary[Group[Node] => Group[Node]] =
    Arbitrary(
      for {
        gnode <- groupNode.arbitrary
      } yield (_: Group[Node]) => gnode
    )

  implicit val arbFnGroupElem: Arbitrary[Group[Elem] => Group[Elem]] =
    Arbitrary(
      for {
        gelem <- groupElem.arbitrary
      } yield (_: Group[Elem]) => gelem
    )

  implicit val arbE2E: Arbitrary[Elem => Elem] =
    Arbitrary(
      for {
        elem <- arbElem.arbitrary
      } yield (_: Elem) => elem
    )

  implicit val elemEqual = new Equal[Elem] {
    override def equal(a1: Elem, a2: Elem): Boolean = a1 == a2
  }

  implicit val nodeEqual = new Equal[Node] {
    override def equal(a1: Node, a2: Node): Boolean = a1 == a2
  }

  implicit val groupNodeEqual = new Equal[Group[Node]] {
    override def equal(a1: Group[Node], a2: Group[Node]): Boolean = a1 == a2
  }

  implicit val groupElemEqual = new Equal[Group[Elem]] {
    override def equal(a1: Group[Elem], a2: Group[Elem]): Boolean = a1 == a2
  }

  val numProcessors = Runtime.getRuntime.availableProcessors
  implicit val params: Parameters = set(workers = numProcessors * 2, maxSize = 25)
}

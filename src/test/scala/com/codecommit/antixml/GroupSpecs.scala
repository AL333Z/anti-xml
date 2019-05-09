/*
 * Copyright (c) 2011, Daniel Spiewak
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer. 
 * - Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 * - Neither the name of "Anti-XML" nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.codecommit.antixml

import org.specs2.mutable._
import org.specs2.ScalaCheck
import org.scalacheck._
import org.specs2.matcher.ResultMatchers
import scala.language.reflectiveCalls

class GroupSpecs extends Specification with ScalaCheck with XMLGenerators with UtilGenerators with LowPrioritiyImplicits with ResultMatchers {
  import XML._
  
  lazy val numProcessors = Runtime.getRuntime.availableProcessors()
  implicit val params = set(workers = numProcessors, maxSize = 15)      // doesn't need to be so large

  "utility methods on Group" >> {
    implicit val arbInt = Arbitrary(Gen.choose(0, 10))

    "map with non-Node result should produce an IndexedSeq" in {
      val group = Group(<parent>child</parent>.convert)
      val res = group map { _.name }
      validate[scala.collection.immutable.IndexedSeq[String]](res)
      res mustEqual Vector("parent")
    }
    
    "foreach should traverse group" in prop { (xml: Group[Node]) =>
      val b = Vector.newBuilder[Node]
      xml foreach {b += _}
      val v = b.result
      val results = for(i <- 0 until xml.length) yield v(i) mustEqual xml(i)
      val r = (v.length mustEqual xml.length) +: results
      r must beSuccessful
    }
  }
  
  "Group.conditionalFlatMapWithIndex" should {
    
    "work with simple replacements" in prop { (xml: Group[Node]) =>
      def f(n: Node, i: Int): Option[Seq[Node]] = n match {
        case n if (i & 1) == 0 => None
        case e: Elem => Some(Seq(e.withName(e.name.toUpperCase())))
        case n => None
      }
      val cfmwi = xml.conditionalFlatMapWithIndex(f)
      val equiv = xml.zipWithIndex.flatMap {case (n,i) => f(n,i).getOrElse(Seq(n))}

      Seq(
        Vector(cfmwi:_*) mustEqual Vector(equiv:_*),
        cfmwi.length mustEqual xml.length
      ) must beSuccessful
    }
    
    "work with complex replacements" in prop { (xml: Group[Node]) =>
      def f(n: Node, i: Int): Option[Seq[Node]] = n match {
        case n if (i & 1) == 0 => None
        case _ if (i & 2) == 0 => Some(Seq())
        case e: Elem => Some(Seq(e.withName(e.name.name+"MODIFIED"), e, e))
        case n => Some(Seq(n, n, n))
      }
      val cfmwi = xml.conditionalFlatMapWithIndex(f)
      val equiv = xml.zipWithIndex.flatMap {case (n,i) => f(n,i).getOrElse(Seq(n))}
      
      val expectedDels = (xml.length + 2) >>> 2
      val expectedTripples = (xml.length) >>> 2
      val expectedLength = xml.length - expectedDels + 2*expectedTripples

      Seq(
        Vector(cfmwi:_*) mustEqual Vector(equiv:_*),
        cfmwi.length mustEqual expectedLength
      ) must beSuccessful
    }
  }

  "canonicalization" should {
    import Node.hasOnlyValidChars
    
    "merge two adjacent text nodes" in prop { (left: String, right: String) =>
      if (hasOnlyValidChars(left + right)) {
        Group(Text(left), Text(right)).canonicalize mustEqual Group(Text(left + right))
        Group(CDATA(left), CDATA(right)).canonicalize mustEqual Group(CDATA(left + right))
      } else {
        Text(left + right) must throwAn[IllegalArgumentException]
      }
    }
    
    "merge two adjacent text nodes at end of Group" in prop { (left: String, right: String) =>
      if (hasOnlyValidChars(left + right)) {
        Group(elem("foo"), elem("bar", Text("test")), Text(left), Text(right)).canonicalize mustEqual Group(elem("foo"), elem("bar", Text("test")), Text(left + right))
        Group(elem("foo"), elem("bar", Text("test")), CDATA(left), CDATA(right)).canonicalize mustEqual Group(elem("foo"), elem("bar", Text("test")), CDATA(left + right))
      } else {
        Text(left + right) must throwAn[IllegalArgumentException]
      }
    }
    
    "merge two adjacent text nodes at beginning of Group" in prop { (left: String, right: String) =>
      if (hasOnlyValidChars(left + right)) {
        Group(Text(left), Text(right), elem("foo"), elem("bar", Text("test"))).canonicalize mustEqual Group(Text(left + right), elem("foo"), elem("bar", Text("test")))
        Group(CDATA(left), CDATA(right), elem("foo"), elem("bar", Text("test"))).canonicalize mustEqual Group(CDATA(left + right), elem("foo"), elem("bar", Text("test")))
      } else {
        Text(left + right) must throwAn[IllegalArgumentException]
      }
    }
    
    "merge two adjacent text nodes at depth" in prop { (left: String, right: String) =>
      if (hasOnlyValidChars(left + right)) {
        Group(elem("foo", elem("bar", Text(left), Text(right)))).canonicalize mustEqual Group(elem("foo", elem("bar", Text(left + right))))
        Group(elem("foo", elem("bar", CDATA(left), CDATA(right)))).canonicalize mustEqual Group(elem("foo", elem("bar", CDATA(left + right))))
      } else {
        Text(left + right) must throwAn[IllegalArgumentException]
      }
    }
    
    "not merge adjacent text and cdata nodes" in prop { (left: String, right: String) =>
      if (hasOnlyValidChars(left + right)) {
        Group(CDATA(left), Text(right)).canonicalize mustEqual Group(CDATA(left), Text(right))
        Group(Text(left), CDATA(right)).canonicalize mustEqual Group(Text(left), CDATA(right))
      } else {
        Text(left + right) must throwAn[IllegalArgumentException]
      }
    }
    
    "always preserve serialized equality" in prop { g: Group[Node] =>
      g.canonicalize.toString mustEqual g.toString
    }
  }
  
  def validate[Expected] = new {
    def apply[A](a: A)(implicit evidence: A =:= Expected) = evidence must not beNull
  }

  def elem(name: String, children: Node*) = Elem(None, name, children = Group(children: _*))

  val anyElem: Selector[Elem] = Selector {case e: Elem => e}
}

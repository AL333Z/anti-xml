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

import org.scalacheck._
import org.specs2.ScalaCheck
import org.specs2.mutable._
import org.specs2.scalacheck.Parameters
import scala.language.reflectiveCalls

import scala.collection.mutable.LinkedHashMap

class AttributesSpecs extends Specification with ScalaCheck with XMLGenerators {

  import Prop._

  implicit val arbString = Arbitrary(genSaneString)

  "attribute sets" should {
    "define empty on arbitrary instances" in prop { attrs: Attributes =>
      val result = attrs.empty
      validate[Attributes](result)
      result must beEmpty
    }

    "support addition of qname attrs" in prop { (attrs: Attributes, name: QName, value: String) =>
      val attrsSafe = attrs - name
      val attrs2: Attributes = attrsSafe + (name -> value)
      attrs2 must havePairs(attrsSafe.toSeq: _*)
      attrs2 must havePair(name -> value)
    }

    "support addition of string attrs" in prop { (attrs: Attributes, x: QName, value: String) =>
      val name = x.name
      val attrsSafe = attrs - name
      val attrs2: Attributes = attrsSafe + (name -> value)
      attrs2 must havePairs(attrsSafe.toSeq: _*)
      attrs2 must havePair(QName(None, name) -> value)
    }

    "support multiple addition of qname attrs" in prop { (attrs: Attributes, n1: QName, v1: String, n2: QName, v2: String, n3: QName, v3: String) =>
      val attrsSafe = attrs - n1 - n2 - n3
      val attrs2: Attributes = attrsSafe + (n1 -> v1, n2 -> v2, n3 -> v3)
      val baseline = Map(attrs.toSeq: _*) + (n1 -> v1, n2 -> v2, n3 -> v3)
      attrs2 must havePairs(baseline.toSeq: _*)
    }

    "support multiple addition of qname attrs" in prop { (attrs: Attributes, x1: QName, v1: String, x2: QName, v2: String, x3: QName, v3: String) =>
      val (n1, n2, n3) = (x1.name, x2.name, x2.name)
      val attrsSafe = attrs - n1 - n2 - n3
      val attrs2: Attributes = attrsSafe + (n1 -> v1, n2 -> v2, n3 -> v3)
      val baseline = Map(attrs.toSeq: _*) + (QName(None, n1) -> v1, QName(None, n2) -> v2, QName(None, n3) -> v3)
      attrs2 must havePairs(baseline.toSeq: _*)
    }

    "support update of qname attrs" in prop { (attrs: Attributes, name: QName, value: String) =>
      val attrsSafe = attrs - name
      val attrs2: Attributes = attrsSafe.updated(name, value)
      attrs2 must havePairs(attrsSafe.toSeq: _*)
      attrs2 must havePair(name -> value)
    }

    "support update of string attrs" in prop { (attrs: Attributes, x: QName, value: String) =>
      val name = x.name
      val attrsSafe = attrs - name
      val attrs2: Attributes = attrsSafe.updated(name, value)
      attrs2 must havePairs(attrsSafe.toSeq: _*)
      attrs2 must havePair(QName(None, name) -> value)
    }


    "produce most specific Map with non-String value" in prop { attrs: Attributes =>
      val value = new AnyRef
      val attrsSafe = attrs - "foo"
      val attrs2 = attrsSafe + (QName(None, "foo") -> value)
      validate[Map[QName, AnyRef]](attrs2)
      attrs2 must havePairs(attrsSafe.toSeq: _*)
      attrs2 must havePair(QName(None, "foo") -> value)
    }

    "support removal of qname attrs" in {
      implicit val data = for {
        attrs <- Arbitrary.arbitrary[Attributes]
        if !attrs.isEmpty
        key <- Gen.oneOf(attrs.keys.toSeq)
      } yield (attrs, key)

      prop { pair: (Attributes, QName) =>
        val (attrs, key) = pair
        val attrs2 = attrs - key
        val expected = attrs filterKeys (key !=)
        attrs2 must havePairs(expected.toSeq: _*)
        attrs2 must not(beDefinedAt(key))
      }
    }

    "support retrieval of attributes by qname" in prop { (attrs: Attributes, key: QName) =>
      val result = attrs get key
      result mustEqual (attrs find { case (`key`, _) => true case _ => false } map {
        _._2
      })
    }

    "support retrieval of attributes by string" in prop { (attrs: Attributes, key: String) =>
      val result = attrs get key
      result mustEqual (attrs find { case (QName(None, `key`), _) => true case _ => false } map {
        _._2
      })
    }

    "produce Attributes from collection utility methods returning compatible results" in {
      val attrs = Attributes("foo" -> "bar", "baz" -> "bin")
      val attrs2 = attrs map { case (k, v) => k -> (v + "42") }
      validate[Attributes](attrs2)
      attrs2 must havePairs(QName(None, "foo") -> "bar42", QName(None, "baz") -> "bin42")
    }

    "produce Map from collection utility methods returning incompatible results" in {
      val attrs = Attributes("foo" -> "bar", "baz" -> "bin")
      val attrs2 = attrs map { case (k, v) => k -> 42 }
      validate[Map[QName, Int]](attrs2)
      attrs2 must havePairs(QName(None, "foo") -> 42, QName(None, "baz") -> 42)
    }

    "preserve build order" in {
      forAll(genAttributeList) { entries: List[(QName, String)] =>
        val attrs = Attributes(entries: _*)
        val expectedOrder = LinkedHashMap(entries: _*).toList
        //Converting to list just in case equals is overridden
        List(attrs.toSeq: _*) mustEqual expectedOrder
      }
    }
  }

  "qualified names" should {
    "implicitly convert from String" in prop { str: String =>
      val qn: QName = str
      qn.prefix must beNone
      qn.name mustEqual str
    }
  }

  def validate[Expected] = new {
    def apply[A](a: A)(implicit evidence: A =:= Expected) = evidence must not beNull
  }

  val numProcessors = Runtime.getRuntime.availableProcessors
  implicit val params: Parameters = set(workers = numProcessors)
}

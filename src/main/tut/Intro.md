anti-xml + Monocle = XMLPath
============================

Why?
====

scala-xml is not good enought
-----------------------------

- unintuitive api to update an xml hierarchy
- unfriendly type hierarchy

![](http://www.codecommit.com/blog/wp-content/uploads/2010/05/s6EW-5XuGuUAjHDi-zmvofQ.png)

anti-xml, just why?
===================

- cleaner ADT:

```haskell
  type Prefix = Maybe String
  type Scope = Map String String

  data Node = ProcInstr String String
            | Elem Prefix String Attributes Scope (Group Node)
            | Text String
            | CDATA String
            | EntityRef String
```

- Zippers to navigate the document (they're cool, but out of topic in this session)
- More type-safety
- Original author [Daniel Spiewak](https://github.com/djspiewak)
- I forked from [this other fork](https://github.com/arktekk/anti-xml), bumped to Scala 2.11 and started experimenting

Monocle - Optics
================

![](https://raw.github.com/julien-truffaut/Monocle/master/image/class-diagram.png)

[Spoiler](http://julien-truffaut.github.io/Monocle/optics.html)

Lens
----

```scala
object Lens {
  def apply[S, A](get: S => A)(set: A => S => S): Lens[S, A] = ???
}
```

A Lens is an optic used to zoom inside a Product, e.g. case class.

```tut
import monocle.Lens
import monocle.macros.GenLens

case class Address(number: Int, streetName: String)

val streetNumber = Lens[Address, Int](_.number)(n => a => a.copy(number = n))
// or  GenLens[Address](_.number)

val address = Address(10, "High Street")
streetNumber.get(address)
streetNumber.set(5)(address)
streetNumber.modify(_ + 1)(address)

```

Lens composition
----------------

```tut
case class Person(name: String, age: Int, address: Address)
val john = Person("John", 20, address)

val address = GenLens[Person](_.address)
(address composeLens streetNumber).get(john)
(address composeLens streetNumber).set(2)(john)

```

Optional
--------

Similar to Lens, but the element that the Optional focuses on may not exist.

```scala
object Optional {
   def apply[S, A](_getOption: S => Option[A])(_set: A => S => S): Optional[S, A] = ???
}
```

Prism
-----

A Prism is an optic used to select part of a Sum type (also known as Coproduct), e.g. sealed trait.

```scala
object Prism {
  def apply[S, A](_getOption: S => Option[A])(_reverseGet: A => S): Prism[S, A] = ???
}
```

```tut
sealed trait Json
case object JNull extends Json
case class JStr(v: String) extends Json
case class JNum(v: Double) extends Json
case class JObj(v: Map[String, Json]) extends Json

import monocle.Prism

val jStr = Prism[Json, String]{
  case JStr(v) => Some(v)
  case _       => None
}(JStr)

// or Prism.partial[Json, String]{case JStr(v) => v}(JStr)

jStr("hello")
jStr.getOption(JStr("Hello"))
jStr.getOption(JNum(3.2))
```

Traversal
---------

A Traversal is the generalisation of an Optional to several targets. 
In other word, a Traversal allows to focus from a type S into 0 to n values of type A.

The most common example of a Traversal would be to focus into all elements inside of a container (List, Option, ...). 

```tut
import monocle.Traversal
import scalaz.std.list._   // to get the Traverse instance for List

val xs = List(1,2,3,4,5)

val eachL = Traversal.fromTraverse[List, Int]
eachL.set(0)(xs)
eachL.modify(_ + 1)(xs)
```

XMLPath
=======

- A simple-but-powerful DSL to manipulate an XML
- Supporting only `Elem` manipulation (no `ProcInstr`, `Text`, `CDATA`, `EntityRef`)
- Typesafe
- Inspired by Circe/JsonPath by Julien Truffaut ![](https://julien-truffaut.github.io/jsonpath.pres/#1)

Illumination
------------

Why don't I just "port JsonPath to XML"?

Not possible. XML is far less structured than json. 
But we can view an `Elem` as a container which contains a list of other `Elem`s.

Remember `Traversal`?

```scala
final case class XMLTraversalPath(e: Traversal[Elem, Elem])
```

You're welcome.

Samples
=======

```tut
import com.codecommit.antixml.XMLPath._
import com.codecommit.antixml._
```

Get all children
----------------

```tut
val input1 =
  <A Attr="01234">
    <B>
      <C Attr="C"></C>
      <C Attr="C"></C>
    </B>
  </A>.convert

root.B.C.getAll(input1)
```

Return nothing if the selected nested child is missing
------------------------------------------------------

```tut
root.B.Missing.getAll(input1)

// set a node multiple times"

val input2 =
  <A Attr="01234">
    <B>B</B>
    <B>B</B>
  </A>.convert

root.B.set(<Other/>.convert)(input2)
```

Modify a node in a nested child
-------------------------------

```tut
val input3 =
  <A Attr="01234">
    <B>
      <C Attr="Yep"/>
      <C Attr="Nope"/>
    </B>
  </A>.convert

val res = root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input3)
```

Modify a node in a nested child
-------------------------------

```tut
val input4 =
  <A Attr="01234">
    <B>
      <C Attr="Yep"/>
      <C Attr="Nope"/>
    </B>
  </A>.convert

root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input4)
```

Modify a missing node
---------------------

```tut
val input5 =
  <A Attr="01234">
    <C></C>
  </A>.convert
   
root.Missing.modify(_.addAttributes(Seq(("OrderLinesAttr", "hello"))))(input5)
```

Modify a nested node with index
------------------------------------

```tut

val input6 =
  <A Attr="01234">
    <B>
      <C/>
      <C>
        <D>
          <E Attr="E0"/>
          <E Attr="E1"/>
        </D>
        <D>
        </D>
        <D>
          <E Attr="E0"/>
        </D>
      </C>
    </B>
  </A>.convert

root.B(0).C(1).D(2).E.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input6)
```

References
==========

- On maven central -> "com.al333z" %% "anti-xml" % "0.7.5"

- [anti-xml fork](https://github.com/AL333Z/anti-xml)
- [Monocle](https://github.com/julien-truffaut/Monocle)
- [Circe/JsonPath](https://github.com/circe/circe)

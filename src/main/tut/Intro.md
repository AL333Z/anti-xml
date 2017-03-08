XMLPath - A Scala typesafe XML dsl
====================================

Topics
------

- anti-xml, a scala-xml replacement
- monocle (lens and other optics), aka how to get/set/update immutable data structures
- scala dynamics, an unknown language feature

------

The problem
===========

Typesafe xml manipulation, without boilerplate.

Out of scope
=============

Parsing.
This session will cover only XML to XML transformations.

------

What's wrong with plain scala.xml?
====================================

- unintuitive api, especially when you neeed to update an elem
  - [SO: Modifying nested elements in xml](http://stackoverflow.com/questions/970675/scala-modifying-nested-elements-in-xml)
  - [SO: How to change attribute on Scala XML Element](http://stackoverflow.com/questions/2569580/how-to-change-attribute-on-scala-xml-element)
- unfriendly type hierarchy
- not so typesafe

------

Let's try with an extension method-based solution...

```scala
object ElemOps {

  implicit class ExtendedElem(val value: Elem) extends AnyVal {
    def findOrCreateElem(label: String): Elem = {
      value.child
        .find(_.label == label)
        .getOrElse(XML.loadString(s"<$label/>"))
        .asInstanceOf[Elem]
    }

    def addAttribute(label: String, name: String, newValue: String): Elem = {
      val elem: Elem = value.findOrCreateElem(label)
      val attributes: MetaData = elem.attributes.append(new UnprefixedAttribute(name, newValue, Null))
      val updatedElem = Elem(elem.prefix, elem.label, attributes, elem.scope, true, elem.child: _*)

      val orderElem = Elem(value.prefix, value.label, value.attributes, value.scope, true, value.child: _*)
      orderElem.copy(
        child = orderElem.child.filterNot(_.label == label).+:(updatedElem)
      )
    }
}
```

Too. Much. Pain.

-----

anti-xml, just why?
===================

- The original author is [Daniel Spiewak](https://github.com/djspiewak)
- I forked from [this other fork](https://github.com/arktekk/anti-xml)
- Provide abstractions to declaratively navigate the document: Zippers (cool, but out of topic in this session)
- More type-safety and more friendly operators/methods
- cleaner/simpler ADT:

```scala
    sealed trait Node
    case class ProcInstr(target: String, data: String) extends Node
    case class Elem(prefix: Option[String], name: String, attrs: Attributes, namespaces: NamespaceBinding, children: Group[Node]) extends Node
    case class Text(text: String) extends Node
    case class CDATA(text: String) extends Node
    case class EntityRef(entity: String) extends Node
```

Nice, but we were talking about manipulations..

------

Monocle
=======

A set of typeclasses that let us get/set/update immutable data structures.

![](https://raw.github.com/julien-truffaut/Monocle/master/image/class-diagram.png)

[Spoiler](http://julien-truffaut.github.io/Monocle/optics.html)

------

Lens
====

```scala
object Lens {
  def apply[S, A](get: S => A)(set: A => S => S): Lens[S, A] = ???
}
```

A Lens is an optic used to zoom inside a Product, e.g. case class.

```tut:invisible
import monocle.Lens
import monocle.macros.GenLens
```

Because `case class` `.copy` method alone doesn't scale well..

------

Let's define some adts

```tut:silent
  case class Address(number: Int, streetName: String)
  case class Person(name: String, age: Int, address: Address)
  case class Employee(person: Person, jobTitle: String)

  val address = Address(10, "High Street")
  val john = Person("John", 20, address)
  val employee = Employee(john, "Senior vegetarian griller")
```

---------

Why should I even care about `Lens`?

```scala
  val updatedEmployee = employee.copy(person =
      employee.person.copy(
        address = john.address.copy(number = 5)
      )
  )
```

Nobody likes boilerplate code.

```tut
val address2streetNumber = Lens[Address, Int](_.number)(n => a => a.copy(number = n))
val address2streetNumber2 = GenLens[Address](_.number)

address2streetNumber.get(address)
address2streetNumber.set(5)(address)
address2streetNumber.modify(_ + 1)(address)
```

------

Lens composition
================

And we can go further, because composition and typesafety just works.

```tut
val person2address = GenLens[Person](_.address)
val person2StreetNumber = person2address composeLens address2streetNumber

person2StreetNumber.get(john)
person2StreetNumber.set(2)(john)
```

------

Optional
========

Similar to `Lens`, but the element that the `Optional` focuses on may not exist.

```scala
object Optional {
   def apply[S, A](_getOption: S => Option[A])(_set: A => S => S): Optional[S, A] = ???
}
```

Prism
=====

A `Prism` is an optic used to select part of a Sum type (also known as Coproduct), e.g. sealed trait.

```scala
object Prism {
  def apply[S, A](_getOption: S => Option[A])(_reverseGet: A => S): Prism[S, A] = ???
}
```

```tut:invisible
import monocle.Prism
```

-----

Let's define a simple hierarchy

```tut:silent
sealed trait Json
case object JNull extends Json
case class JStr(v: String) extends Json
case class JNum(v: Double) extends Json
case class JObj(v: Map[String, Json]) extends Json
```

And have some fun with `Prism`

```tut
val jStr = Prism[Json, String]{
  case JStr(v) => Some(v)
  case _       => None
}(JStr)

jStr("hello")
jStr.getOption(JStr("Hello"))
jStr.getOption(JNum(3.2))
```

---------

Traversal
=========

A `Traversal` is the generalisation of an Optional to several targets.
In other word, a `Traversal` allows to focus from a type S into 0 to n values of type A.

The most common example of a `Traversal` would be to focus into all elements inside of a container (List, Option, ...).

```tut:invisible
import monocle.Traversal
```

```tut
import scalaz.std.list._   // to get the Traverse instance for List

val xs = List(1,2,3,4,5)

val eachNumber = Traversal.fromTraverse[List, Int]

eachNumber.set(0)(xs)
eachNumber.modify(_ + 1)(xs)
```

---------

Putting things together: XMLPath
=======

- A simple-but-powerful DSL to manipulate an XML
- Supporting only `Elem` manipulation (no `ProcInstr`, `Text`, `CDATA`, `EntityRef`)
- Typesafe
- Inspired by [Circe/JsonPath](https://julien-truffaut.github.io/jsonpath.pres/#1) by Julien Truffaut
- Let's you access/manipulate a Json using a simple dsl

------------

Illumination
============

Why don't I just "port JsonPath to XML"?
- Not possible. XML is far less structured than json and complex.


So, let's focus only on the `Elem` part.
And, **we can view an `Elem` as a container which contains a list of other `Elem`s (children)**.

Remember `Traversal`?

```scala
final case class XMLTraversalPath(e: Traversal[Elem, Elem])
```

Just add some `Dynamic`, and ...
You're welcome.

----

Samples
=======

```tut:silent
import com.codecommit.antixml.XMLPath._
import com.codecommit.antixml._
```

Get all children

```tut:silent
val input1 =
  <A Attr="01234">
    <B>
      <C Attr="C"></C>
      <C Attr="C"></C>
    </B>
    <B>
      <C Attr="C1"></C>
    </B>
  </A>.convert
```

```tut
root.B.C.getAll(input1)
```

------------

Return nothing if the selected nested child is missing

```tut
root.B.Missing.getAll(input1)
```

--------------------------

Set a node multiple times

```tut:silent
val input2 =
  <A Attr="01234">
    <B>B</B>
    <B>B</B>
  </A>.convert
```

```tut
root.B.set(<Other/>.convert)(input2)
```

--------------------------

Modify a node in a nested child

```tut:silent
val input3 =
  <A Attr="01234">
    <B>
      <C Attr="Yep"/>
      <C Attr="Nope"/>
    </B>
  </A>.convert
```

```tut
val res = root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input3)
```

---------------------

Modify a missing node

```tut:silent
val input5 =
  <A Attr="01234">
    <C></C>
  </A>.convert
```

```tut
root.Missing.modify(_.addAttributes(Seq(("OrderLinesAttr", "hello"))))(input5)
```

------------------------------------

Modify a nested node with index

```tut:silent
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
```

------

```tut
root.B(0).C(1).D(2).E.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input6)
```

References
==========

- On maven central -> `"com.al333z" %% "anti-xml" % "0.7.5"`
- [anti-xml fork](https://github.com/AL333Z/anti-xml)
- [Monocle](https://github.com/julien-truffaut/Monocle)
- [Circe/JsonPath](https://github.com/circe/circe)

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

```scala
scala> import monocle.Lens
import monocle.Lens

scala> import monocle.macros.GenLens
import monocle.macros.GenLens

scala> case class Address(number: Int, streetName: String)
defined class Address

scala> val streetNumber = Lens[Address, Int](_.number)(n => a => a.copy(number = n))
streetNumber: monocle.Lens[Address,Int] = monocle.PLens$$anon$7@61c9e5ea

scala> // or  GenLens[Address](_.number)
     | 
     | val address = Address(10, "High Street")
address: Address = Address(10,High Street)

scala> streetNumber.get(address)
res2: Int = 10

scala> streetNumber.set(5)(address)
res3: Address = Address(5,High Street)

scala> streetNumber.modify(_ + 1)(address)
res4: Address = Address(11,High Street)
```

Lens composition
----------------

```scala
scala> case class Person(name: String, age: Int, address: Address)
defined class Person

scala> val john = Person("John", 20, address)
john: Person = Person(John,20,Address(10,High Street))

scala> val address = GenLens[Person](_.address)
address: monocle.Lens[Person,Address] = $anon$1@26a422d3

scala> (address composeLens streetNumber).get(john)
res5: Int = 10

scala> (address composeLens streetNumber).set(2)(john)
res6: Person = Person(John,20,Address(2,High Street))
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

```scala
scala> sealed trait Json
defined trait Json

scala> case object JNull extends Json
defined object JNull

scala> case class JStr(v: String) extends Json
defined class JStr

scala> case class JNum(v: Double) extends Json
defined class JNum

scala> case class JObj(v: Map[String, Json]) extends Json
defined class JObj

scala> import monocle.Prism
import monocle.Prism

scala> val jStr = Prism[Json, String]{
     |   case JStr(v) => Some(v)
     |   case _       => None
     | }(JStr)
jStr: monocle.Prism[Json,String] = monocle.Prism$$anon$7@ee5a615

scala> // or Prism.partial[Json, String]{case JStr(v) => v}(JStr)
     | 
     | jStr("hello")
res9: Json = JStr(hello)

scala> jStr.getOption(JStr("Hello"))
res10: Option[String] = Some(Hello)

scala> jStr.getOption(JNum(3.2))
res11: Option[String] = None
```

Traversal
---------

A Traversal is the generalisation of an Optional to several targets. 
In other word, a Traversal allows to focus from a type S into 0 to n values of type A.

The most common example of a Traversal would be to focus into all elements inside of a container (List, Option, ...). 

```scala
scala> import monocle.Traversal
import monocle.Traversal

scala> import scalaz.std.list._   // to get the Traverse instance for List
import scalaz.std.list._

scala> val xs = List(1,2,3,4,5)
xs: List[Int] = List(1, 2, 3, 4, 5)

scala> val eachL = Traversal.fromTraverse[List, Int]
eachL: monocle.Traversal[List[Int],Int] = monocle.PTraversal$$anon$5@2e7e596b

scala> eachL.set(0)(xs)
res12: List[Int] = List(0, 0, 0, 0, 0)

scala> eachL.modify(_ + 1)(xs)
res13: List[Int] = List(2, 3, 4, 5, 6)
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

```scala
scala> import com.codecommit.antixml.XMLPath._
import com.codecommit.antixml.XMLPath._

scala> import com.codecommit.antixml._
import com.codecommit.antixml._
```

Get all children
----------------

```scala
scala> val input1 =
     |   <A Attr="01234">
     |     <B>
     |       <C Attr="C"></C>
     |       <C Attr="C"></C>
     |     </B>
     |   </A>.convert
input1: com.codecommit.antixml.Elem =
<A Attr="01234">
    <B>
      <C Attr="C"/>
      <C Attr="C"/>
    </B>
  </A>

scala> root.B.C.getAll(input1)
res14: List[com.codecommit.antixml.Elem] = List(<C Attr="C"/>, <C Attr="C"/>)
```

Return nothing if the selected nested child is missing
------------------------------------------------------

```scala
scala> root.B.Missing.getAll(input1)
res15: List[com.codecommit.antixml.Elem] = List()

scala> // set a node multiple times"
     | 
     | val input2 =
     |   <A Attr="01234">
     |     <B>B</B>
     |     <B>B</B>
     |   </A>.convert
input2: com.codecommit.antixml.Elem =
<A Attr="01234">
    <B>B</B>
    <B>B</B>
  </A>

scala> root.B.set(<Other/>.convert)(input2)
res18: com.codecommit.antixml.Elem =
<A Attr="01234">
    <Other/>
    <Other/>
  </A>
```

Modify a node in a nested child
-------------------------------

```scala
scala> val input3 =
     |   <A Attr="01234">
     |     <B>
     |       <C Attr="Yep"/>
     |       <C Attr="Nope"/>
     |     </B>
     |   </A>.convert
input3: com.codecommit.antixml.Elem =
<A Attr="01234">
    <B>
      <C Attr="Yep"/>
      <C Attr="Nope"/>
    </B>
  </A>

scala> val res = root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input3)
res: com.codecommit.antixml.Elem =
<A Attr="01234">
    <B>
      <C Attr="Yep" OtherAttr="bar"/>
      <C Attr="Nope" OtherAttr="bar"/>
    </B>
  </A>
```

Modify a node in a nested child
-------------------------------

```scala
scala> val input4 =
     |   <A Attr="01234">
     |     <B>
     |       <C Attr="Yep"/>
     |       <C Attr="Nope"/>
     |     </B>
     |   </A>.convert
input4: com.codecommit.antixml.Elem =
<A Attr="01234">
    <B>
      <C Attr="Yep"/>
      <C Attr="Nope"/>
    </B>
  </A>

scala> root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input4)
res19: com.codecommit.antixml.Elem =
<A Attr="01234">
    <B>
      <C Attr="Yep" OtherAttr="bar"/>
      <C Attr="Nope" OtherAttr="bar"/>
    </B>
  </A>
```

Modify a missing node
---------------------

```scala
scala> val input5 =
     |   <A Attr="01234">
     |     <C></C>
     |   </A>.convert
input5: com.codecommit.antixml.Elem =
<A Attr="01234">
    <C/>
  </A>

scala> root.Missing.modify(_.addAttributes(Seq(("OrderLinesAttr", "hello"))))(input5)
res20: com.codecommit.antixml.Elem =
<A Attr="01234">
    <C/>
  </A>
```

Modify a nested node with index
------------------------------------

```scala
scala> val input6 =
     |   <A Attr="01234">
     |     <B>
     |       <C/>
     |       <C>
     |         <D>
     |           <E Attr="E0"/>
     |           <E Attr="E1"/>
     |         </D>
     |         <D>
     |         </D>
     |         <D>
     |           <E Attr="E0"/>
     |         </D>
     |       </C>
     |     </B>
     |   </A>.convert
input6: com.codecommit.antixml.Elem =
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
  </A>

scala> root.B(0).C(1).D(2).E.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input6)
res21: com.codecommit.antixml.Elem =
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
          <E Attr="E0" OtherAttr="bar"/>
        </D>
      </C>
    </B>
  </A>
```

References
==========

- On maven central -> "com.al333z" %% "anti-xml" % "0.7.5"

- [anti-xml fork](https://github.com/AL333Z/anti-xml)
- [Monocle](https://github.com/julien-truffaut/Monocle)
- [Circe/JsonPath](https://github.com/circe/circe)

package com.codecommit.antixml

import com.codecommit.antixml.XMLPath._
import org.specs2.mutable._

class XMLPathSpecs extends Specification {

  "XMLPath" should {

    "return all elems when the we ask for the root" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.getAll(input) mustEqual List(input)
    }

    "return the child" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.B.getAll(input) mustEqual List(<B>B</B>.convert)
    }

    "return the children" in {
      val input =
        <A Attr="01234">
          <B>B</B>
          <B>B</B>
        </A>.convert

      root.B.getAll(input) mustEqual List(<B>B</B>.convert, <B>B</B>.convert)
    }

    "return the selected nested children" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="C"></C>
            <C Attr="C"></C>
          </B>
        </A>.convert

      root.B.C.getAll(input) mustEqual List(<C Attr="C"></C>.convert, <C Attr="C"></C>.convert)
    }

    "return nothing if the selected nested child is missing" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.B.Missing.getAll(input) mustEqual Nil
    }

    "set a node" in {
      val input = <A Attr="01234">
        <B/>
      </A>.convert

      root.B.set(<Other/>.convert)(input) mustEqual <A Attr="01234">
        <Other/>
      </A>.convert
    }

    "set a node multiple times" in {
      val input =
        <A Attr="01234">
          <B>B</B>
          <B>B</B>
        </A>.convert

      root.B.set(<Other/>.convert)(input) mustEqual
        <A Attr="01234">
          <Other/>
          <Other/>
        </A>.convert
    }

    "set a node in a nested child" in {
      val input =
        <A Attr="01234">
          <B Attr="b">
            <C/>
          </B>
        </A>.convert

      root.B.C.set(<Other/>.convert)(input) mustEqual
        <A Attr="01234">
          <B Attr="b">
            <Other/>
          </B>
        </A>.convert
    }

    "modify a node in a nested child" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="Yep"/>
            <C Attr="Nope"/>
          </B>
        </A>.convert

      val res = root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input)

      res mustEqual
        <A Attr="01234">
          <B>
            <C Attr="Yep" OtherAttr="bar"/>
            <C Attr="Nope" OtherAttr="bar"/>
          </B>
        </A>.convert
    }

    "modify a nested node" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="Yep"></C>
          </B>
        </A>.convert

      val res = root.B.C.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input)

      res mustEqual
        <A Attr="01234">
          <B>
            <C Attr="Yep" OtherAttr="bar"/>
          </B>
        </A>.convert
    }

    "modify a missing node" in {
      val input =
        <A Attr="01234">
          <C></C>
        </A>.convert

      val res = root.Missing.modify(_.addAttributes(Seq(("OrderLinesAttr", "hello"))))(input)

      res mustEqual input
    }

    "modify a node in a nested child with index" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="Yep"/>
            <C Attr="Nope"/>
          </B>
        </A>.convert

      val res = root.B.C(0).modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input)

      res mustEqual
        <A Attr="01234">
          <B>
            <C Attr="Yep" OtherAttr="bar"/>
            <C Attr="Nope"/>
          </B>
        </A>.convert
    }

    "modify a very nested node with index" in {
      val input =
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
          .convert

      val res = root.B(0).C(1).D(2).E.modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input)

      res mustEqual
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
          .convert
    }

    "filter" in {
      val input =
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
          .convert

      val res = root.B(0).C(1).D(0).filterChildren(x => x.name == "E" && x.attr("Attr").contains("E1"))(input)

      res mustEqual
        <A Attr="01234">
          <B>
            <C/>
            <C>
              <D>
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
          .convert
    }

    "filterNot" in {
      val input =
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
          .convert

      val res = root.B(0).C(1).D(0).filterNotChildren(x => x.name == "E" && x.attr("Attr").contains("E1"))(input)

      res mustEqual
        <A Attr="01234">
          <B>
            <C/>
            <C>
              <D>
                <E Attr="E0"/>
              </D>
              <D>
              </D>
              <D>
                <E Attr="E0"/>
              </D>
            </C>
          </B>
        </A>
          .convert
    }

  }

}

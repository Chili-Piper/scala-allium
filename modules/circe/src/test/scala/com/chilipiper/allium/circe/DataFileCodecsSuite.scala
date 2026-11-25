package com.chilipiper.allium.circe

import com.chilipiper.allium.model._

class DataFileCodecsSuite extends munit.FunSuite {

  private val A = SpecName("001-a")
  private val B = SpecName("002-b")

  test("out of scope: an obligation of a spec, and a category of one or every spec") {
    val json =
      """[
        |  {"spec": "001-a", "obligation": "value-equality.V", "reason": "case class"},
        |  {"spec": "001-a", "category": "config-default", "reason": "configuration"},
        |  {"category": "surface-exposure", "reason": "no UI"}
        |]""".stripMargin
    assertEquals(
      AlliumJson.outOfScope(json),
      Right(
        List(
          OutOfScope(Some(A), Selector.Id(ObligationId("value-equality.V")), "case class"),
          OutOfScope(Some(A), Selector.Kind("config-default"), "configuration"),
          OutOfScope(None, Selector.Kind("surface-exposure"), "no UI"),
        ),
      ),
    )
  }

  test("out of scope: malformed entries are errors") {
    def error(entry: String): String = AlliumJson.outOfScope(s"[$entry]").swap.getOrElse(fail("expected an error"))
    assert(error("""{"obligation": "value-equality.V", "reason": "r"}""").contains("an obligation needs a spec"))
    assert(error("""{"spec": "001-a", "reason": "r"}""").contains("exactly one of obligation, category"))
    assert(
      error("""{"spec": "001-a", "obligation": "x.Y", "category": "x", "reason": "r"}""")
        .contains("exactly one of obligation, category"),
    )
    assert(error("""{"spec": "001-a", "categroy": "x", "reason": "r"}""").contains("unknown fields categroy"))
    assert(error("""{"spec": "001-a", "category": "x"}""").contains("reason"))
  }

  test("superseded: a construct and the rules that supersede it") {
    val json =
      """[{"spec": "001-a", "construct": "Old",
        |  "by": [{"spec": "002-b", "construct": "New"}, {"spec": "002-b", "construct": "Newer"}]}]""".stripMargin
    assertEquals(
      AlliumJson.superseded(json),
      Right(
        List(
          Supersession(
            ConstructRef(A, "Old"),
            List(ConstructRef(B, "New"), ConstructRef(B, "Newer")),
          ),
        ),
      ),
    )
  }

  test("superseded: an empty `by` and unknown fields are errors") {
    def error(entry: String): String = AlliumJson.superseded(s"[$entry]").swap.getOrElse(fail("expected an error"))
    assert(error("""{"spec": "001-a", "construct": "Old", "by": []}""").contains("names no superseding rule"))
    assert(
      error("""{"spec": "001-a", "construct": "Old", "by": [{"spec": "002-b", "rule": "New"}]}""")
        .contains("unknown fields rule"),
    )
  }

  test("a data file is one JSON array") {
    assert(AlliumJson.outOfScope("[] []").isLeft)
    assert(AlliumJson.superseded("{}").isLeft)
  }

  test("pending: spec, obligation and reason; an unknown field is an error") {
    assertEquals(
      AlliumJson.pending("""[{"spec": "001-a", "obligation": "surface-guarantee.S.G", "reason": "no test yet"}]"""),
      Right(List(Pending(SpecName("001-a"), ObligationId("surface-guarantee.S.G"), "no test yet"))),
    )
    assert(
      AlliumJson
        .pending("""[{"spec": "001-a", "obligation": "x", "reason": "r", "owner": "me"}]""")
        .swap
        .exists(_.contains("unknown fields owner")),
    )
    assert(AlliumJson.pending("""[{"spec": "001-a", "reason": "r"}]""").isLeft)
  }

}

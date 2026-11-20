package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

class DataFilesSuite extends munit.FunSuite {

  test("out of scope: an id needs a spec; comments and malformed lines") {
    val (entries, problems) = DataFiles.parseOutOfScope(
      "oos.txt",
      List(
        "# comment",
        "",
        "001-a value-equality.V  Scala: case class V  # trailing comment",
        "*     category:surface-exposure  no UI",
        "*     value-equality.V  everywhere",
        "001-a only-two-fields",
      ),
    )
    assertEquals(
      entries,
      List(
        OutOfScope(Some(SpecName("001-a")), Selector.Id(ObligationId("value-equality.V")), "Scala: case class V", 3),
        OutOfScope(None, Selector.Kind("surface-exposure"), "no UI", 4),
      ),
    )
    assertEquals(
      problems,
      List(
        "oos.txt:5: an obligation id needs a spec, not `*`",
        "oos.txt:6: expected `<spec|*> <obligation id|category:<kind>> <reason>`, got: 001-a only-two-fields",
      ),
    )
  }

  test("superseded: a construct followed by at least one successor") {
    val (entries, problems) =
      DataFiles.parseSuperseded("s.txt", List("001-a/Old  002-b/New 003-c/Newer", "001-a/Old", "001-a/Old 002-b"))
    assertEquals(
      entries,
      List(
        Supersession(
          ConstructRef(SpecName("001-a"), "Old"),
          List(ConstructRef(SpecName("002-b"), "New"), ConstructRef(SpecName("003-c"), "Newer")),
          1,
        ),
      ),
    )
    assertEquals(problems.size, 2)
  }

}

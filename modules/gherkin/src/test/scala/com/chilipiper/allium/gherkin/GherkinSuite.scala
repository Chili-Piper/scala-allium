package com.chilipiper.allium.gherkin

import com.chilipiper.allium.coverage.Tag
import com.chilipiper.allium.model.{ObligationId, SpecName}

class GherkinSuite extends munit.FunSuite {

  private def feature(text: String): Feature = Feature.parse("features/001-x/y.feature", text.stripMargin)

  private def tagsOf(f: Feature, scenario: String): List[String] =
    f.scenarios.find(_.name == scenario).map(_.tags).getOrElse(fail(s"no scenario '$scenario' in ${f.scenarios}"))

  test("a scenario carries the tags of all its stacked tag lines, with comments and blank lines between them") {
    val f = feature("""Feature: F
        |
        |  @wip
        |  # parked until the fix lands
        |  @slow @db # a trailing comment
        |
        |  Scenario: stacked
        |    Given something
        |""")
    assertEquals(tagsOf(f, "stacked").toSet, Set("@wip", "@slow", "@db"))
  }

  test("a scenario does not pick up the tags of the scenario above it") {
    val f = feature("""Feature: F
        |
        |  @skip
        |  Scenario: first
        |    Given something
        |
        |  Scenario: second
        |    Given something
        |""")
    assertEquals(tagsOf(f, "first"), List("@skip"))
    assertEquals(tagsOf(f, "second"), Nil)
  }

  test("a scenario inherits the tags of its Feature and Rule") {
    val f = feature("""# language: en
        |@ignore
        |Feature: F
        |
        |  Scenario: before the rule
        |    Given something
        |
        |  @pending
        |  Rule: a rule
        |
        |    Scenario: inside the rule
        |      Given something
        |""")
    assertEquals(tagsOf(f, "before the rule"), List("@ignore"))
    assertEquals(tagsOf(f, "inside the rule").toSet, Set("@ignore", "@pending"))
  }

  test("a scenario outline carries the tags of its Examples blocks; an Example is a scenario") {
    val f = feature("""Feature: F
        |
        |  Scenario Outline: outline
        |    Given <x>
        |
        |    Examples: run
        |      | x |
        |      | 1 |
        |
        |    @disabled
        |    Examples: parked
        |      | x |
        |      | 2 |
        |
        |  Example: next
        |    Given something
        |""")
    assertEquals(f.scenarios.map(_.name), List("outline", "next"))
    assertEquals(tagsOf(f, "outline"), List("@disabled"))
    assertEquals(tagsOf(f, "next"), Nil)
  }

  test("keywords and tags inside doc strings are step data, not scenarios") {
    val f = feature("""Feature: F
        |
        |  Scenario: with doc strings
        |    Given the text
        |      '''
        |      Scenario: inside a doc string
        |      @wip
        |      Rule: not a rule
        |      '''
        |    And the markdown
        |      ```json
        |      Example: inside a backtick doc string
        |      ```
        |
        |  Scenario: after
        |    Given something
        |""".replace("'''", "\"\"\""))
    assertEquals(f.scenarios.map(s => s.name -> s.line), List("with doc strings" -> 3, "after" -> 15))
    assertEquals(tagsOf(f, "after"), Nil)
  }

  test("invalid Gherkin is a problem, not a silently empty feature") {
    val f = feature("""Feature: F
        |
        |  Scenario: first
        |    Given something
        |
        |Feature: a second feature in the same file
        |""")
    assert(f.problems.nonEmpty)
    assert(feature("").problems.nonEmpty)
  }

  test("the @obligation tags of a feature belong to the spec of its directory") {
    val f = feature("""Feature: F
        |
        |  @obligation:rule-success.R @slow
        |  Scenario: covered
        |    Given something
        |""")
    assertEquals(
      GherkinCoverage.tags(List(f)),
      List(Tag(SpecName("001-x"), ObligationId("rule-success.R"), "features/001-x/y.feature:4")),
    )
    assertEquals(GherkinCoverage.tags(List(f), _ => None), Nil)
    assertEquals(GherkinCoverage.tags(List(f), tagPrefix = "@ob:"), Nil)
  }

  test("a scenario behind a skip tag is a problem") {
    val f = feature("""Feature: F
        |
        |  @WIP
        |  Scenario: parked
        |    Given something
        |""")
    assertEquals(GherkinCoverage.skippedScenarios(List(f)), List("features/001-x/y.feature:4: 'parked' is tagged @WIP"))
    assertEquals(GherkinCoverage.skippedScenarios(List(f), skipTags = Set.empty), Nil)
  }

}

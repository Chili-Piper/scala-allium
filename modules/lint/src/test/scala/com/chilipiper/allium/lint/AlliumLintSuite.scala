package com.chilipiper.allium.lint

import com.chilipiper.allium.circe.AlliumJson
import com.chilipiper.allium.cli.TestCli
import com.chilipiper.allium.model._
import io.circe.Json

class AlliumLintSuite extends munit.FunSuite {

  private def ast(text: String): Json =
    TestCli.cli.parseText(text).flatMap(AlliumJson.single).fold(e => fail(e), identity)

  test("an `alias/config.param` must name a parameter of the aliased spec's config block") {
    val a = ast("-- allium: 3\nconfig {\n    issuer: String\n}\n")
    val b = ast(
      """-- allium: 3
        |use "./a.allium" as a
        |
        |rule R {
        |    when: X(t)
        |    requires: t = a/config.issuer
        |    requires: t = a/config.isuer
        |    ensures: Done()
        |}
        |""".stripMargin,
    )
    assertEquals(
      AlliumLint.undeclaredConfigReferences(SpecName("b"), b, Map(SpecName("a") -> a).get),
      List("specs/b.allium: a/config.isuer is not declared in the config block of specs/a.allium"),
    )
    assertEquals(AlliumLint.undeclaredConfigReferences(SpecName("b"), b, _ => None), Nil)
  }

  test("errors and fatal warning codes fail; other warnings do not") {
    def d(severity: Severity, code: Option[String]) = Diagnostic(code, severity, "m", None, None)
    val reports = List(
      CheckReport(
        Some("specs/a.allium"),
        List(
          d(Severity.Error, None),
          d(Severity.Warning, Some("allium.config.undefinedReference")),
          d(Severity.Warning, Some("allium.rule.unreachableTrigger")),
          d(Severity.Info, Some("allium.use.unresolvedPath")),
        ),
      ),
    )
    assertEquals(AlliumLint.failing(reports).size, 3)
    assertEquals(AlliumLint.failing(reports, fatalCodes = Set.empty).size, 1)
  }

}

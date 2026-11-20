package com.chilipiper.allium.circe

import com.chilipiper.allium.cli.TestCli
import com.chilipiper.allium.coverage.SupersededMarker
import com.chilipiper.allium.model._

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files

/** The decoders on the real output of the pinned CLI. */
class AlliumJsonSuite extends munit.FunSuite {

  private val spec =
    """-- allium: 3
      |
      |config {
      |    limit: Integer = 3
      |}
      |
      |contract Store {
      |    @invariant UniqueHash
      |        -- every hash is unique
      |    @invariant OldHash
      |        -- @superseded by spec 002
      |}
      |
      |invariant TopLevel {
      |    true
      |}
      |
      |surface S {
      |    facing x: Thing
      |    @guarantee G
      |        -- g
      |}
      |
      |entity Thing {
      |    name: String
      |}
      |
      |rule Later {
      |    -- @superseded by spec 002
      |    when: X(t)
      |    requires: t.name != "x"
      |    ensures: Done()
      |}
      |
      |rule Kept {
      |    when: Y()
      |    if true:
      |        -- @superseded only a branch
      |        ensures: Done()
      |}
      |""".stripMargin

  private def outline(text: String): Outline =
    TestCli.cli.parseText(text).flatMap(AlliumJson.outline).fold(e => fail(e), identity)

  test("`allium parse` decodes to an outline with kinds, names, spans and annotations") {
    val o = outline(spec)
    assertEquals(o.diagnostics.filter(_.isError), Nil)
    assertEquals(
      o.declarations.map(d => d.kind -> d.name),
      List(
        DeclarationKind.Config -> None,
        DeclarationKind.Contract -> Some("Store"),
        DeclarationKind.Invariant -> Some("TopLevel"),
        DeclarationKind.Surface -> Some("S"),
        DeclarationKind.Entity -> Some("Thing"),
        DeclarationKind.Rule -> Some("Later"),
        DeclarationKind.Rule -> Some("Kept"),
      ),
    )
    assertEquals(
      o.declarations(1).annotations.map(a => (a.kind, a.name, a.body)),
      List(
        (AnnotationKind.Invariant, Some("UniqueHash"), List("every hash is unique")),
        (AnnotationKind.Invariant, Some("OldHash"), List("@superseded by spec 002")),
      ),
    )
    val later = o.declarations.find(_.name.contains("Later")).get
    assert(SourceText.of(spec.getBytes(UTF_8), later.span).exists(_.startsWith("rule Later {")))
  }

  test("the comment-line marker finds superseded rules and invariants, not superseded branches") {
    assertEquals(SupersededMarker.commentLine.superseded(spec.getBytes(UTF_8), outline(spec)), Set("OldHash", "Later"))
  }

  test("`allium parse` of a broken spec decodes with its error diagnostic") {
    val o = outline("-- allium: 3\nrule A {\n    when: X()\n")
    assert(o.diagnostics.exists(d => d.isError && d.message.contains("expected '}'")), o.diagnostics)
    assert(o.diagnostics.forall(_.span.isDefined))
  }

  test("`allium plan` decodes to obligations with their spans") {
    val dir = Files.createTempDirectory("allium")
    Files.writeString(dir.resolve("x.allium"), spec)
    val plan = TestCli.cli.plan(dir, "x.allium").flatMap(AlliumJson.plan).fold(e => fail(e), identity)
    val later = plan.obligations.filter(_.construct == "Later")
    assert(later.exists(_.id == ObligationId("rule-success.Later")), later)
    assert(later.exists(_.kind == "rule-failure"), later)
    assert(later.forall(_.span.isDefined))
    assertEquals(plan.obligations.map(_.id).distinct.size, plan.obligations.size)
  }

  test("`allium check` prints one report per spec file") {
    val dir = Files.createTempDirectory("allium")
    Files.createDirectory(dir.resolve("specs"))
    Files.writeString(dir.resolve("specs/a.allium"), spec)
    Files.writeString(dir.resolve("specs/b.allium"), "rule B {\n")
    val reports = TestCli.cli.check(dir, "specs/").flatMap(AlliumJson.check).fold(e => fail(e), identity)
    assertEquals(reports.flatMap(_.specFile).toSet, Set("specs/a.allium", "specs/b.allium"))
    val b = reports.find(_.specFile.contains("specs/b.allium")).get
    assert(b.diagnostics.exists(d => d.isError && d.location.flatMap(_.line).contains(2)), b)
  }

  test("a plan without obligations or with a malformed obligation is an error") {
    assert(AlliumJson.plan("""{"diagnostics": []}""").isLeft)
    assert(AlliumJson.plan("""{"obligations": [{"id": "x"}]}""").isLeft)
    assert(AlliumJson.single("{} {}").isLeft)
    assertEquals(
      AlliumJson.plan(
        """{"obligations": [{"id": "a.B", "category": "c", "source_construct": "B", "description": "d"}]}""",
      ),
      Right(Plan(List(Obligation(ObligationId("a.B"), "c", "B", "d", None)), Nil)),
    )
  }

}

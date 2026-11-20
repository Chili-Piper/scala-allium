package com.chilipiper.allium.examples

import com.chilipiper.allium.circe.AlliumJson
import com.chilipiper.allium.cli.{AlliumCli, Release}
import com.chilipiper.allium.coverage._
import com.chilipiper.allium.gherkin.{Feature, GherkinCoverage}
import com.chilipiper.allium.lint.AlliumLint
import com.chilipiper.allium.model.{ConstructRef, SpecName}

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

/** The README example on `modules/examples/fixture`, a small repository with specs, features and data files. */
class ObligationCoverageSuite extends munit.FunSuite {

  private val build: Path = Paths.get(sys.props("user.dir")).toAbsolutePath
  private val root: Path = build.resolve("modules/examples/fixture")

  private lazy val cli: AlliumCli =
    AlliumCli.download(Release.latest, build.resolve("target/allium-cli")).fold(e => fail(e), identity)

  private lazy val specs: List[Path] =
    Files.list(root.resolve("specs")).iterator.asScala.filter(_.toString.endsWith(".allium")).toList.sorted

  /** A data file that does not exist has no entries. */
  private def data[A](file: String)(decode: String => Either[String, List[A]]): List[A] = {
    val p = root.resolve(file)
    if (Files.exists(p)) decode(Files.readString(p)).fold(e => fail(s"$file: $e"), identity) else Nil
  }

  private def gate(tagFilter: Tag => Boolean): (Reconciliation, List[String]) = {
    val inputs = specs.map { p =>
      val rel = root.relativize(p).toString
      val name = SpecName(p.getFileName.toString.stripSuffix(".allium"))
      val source = Files.readAllBytes(p)
      val plan = cli.plan(root, rel).flatMap(AlliumJson.plan).fold(e => fail(e), identity)
      val outline = cli.parse(root, rel).flatMap(AlliumJson.outline).fold(e => fail(e), identity)
      (
        SpecPlan(name, plan, source),
        SupersededMarker.commentLine.superseded(source, outline).map(ConstructRef(name, _)),
      )
    }
    val features = Feature.readAll(root.resolve("features"), root)
    val outOfScope = data(Layout.default.outOfScopeFile)(AlliumJson.outOfScope)
    val superseded = data(Layout.default.supersededFile)(AlliumJson.superseded)
    val result = Coverage.reconcile(
      inputs.map(_._1),
      GherkinCoverage.tags(features).filter(tagFilter),
      outOfScope,
      superseded,
      inputs.flatMap(_._2).toSet,
    )
    val problems = features.flatMap(_.problems) ++ GherkinCoverage.skippedScenarios(features) ++ result.problems
    (result, problems)
  }

  test("every obligation of the fixture is covered, superseded or out of scope") {
    val (result, problems) = gate(_ => true)
    assertEquals(problems, Nil, (result.reports.map(_.line) :+ result.totalLine).mkString("\n"))
    assertEquals(
      result.reports.map(_.line),
      List(
        "001-orders: 6 obligations, 2 covered, 4 uncovered (out of scope: 2, superseded: 2, missing: 0)",
        "002-orders-v2: 3 obligations, 3 covered, 0 uncovered (out of scope: 0, superseded: 0, missing: 0)",
      ),
    )
  }

  test("a removed tag makes its obligation missing") {
    val (_, problems) = gate(_.id.value != "rule-failure.PlaceOrderChecked.2")
    assertEquals(problems.size, 1, problems)
    assert(problems.head.startsWith("specs/002-orders-v2.allium: rule-failure.PlaceOrderChecked.2 — "), problems)
    assert(
      problems.head.endsWith("no test in features/002-orders-v2/ carries @obligation:rule-failure.PlaceOrderChecked.2"),
    )
  }

  test("the fixture passes the lint checks") {
    val rels = specs.map(p => SpecName(p.getFileName.toString.stripSuffix(".allium")) -> root.relativize(p).toString)
    val asts = rels.map { case (n, rel) =>
      n -> cli.parse(root, rel).flatMap(AlliumJson.single).fold(e => fail(e), identity)
    }.toMap
    val reports = cli.check(root, "specs/").flatMap(AlliumJson.check).fold(e => fail(e), identity)
    assertEquals(AlliumLint.failing(reports), Nil)
    assertEquals(asts.toList.flatMap { case (n, ast) => AlliumLint.undeclaredConfigReferences(n, ast, asts.get) }, Nil)
  }

}

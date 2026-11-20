package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

import java.nio.charset.StandardCharsets.UTF_8

class CoverageSuite extends munit.FunSuite {

  private def ob(id: String, construct: String): Obligation =
    Obligation(ObligationId(id), id.takeWhile(_ != '.').replace('-', '_'), construct, s"verify $id", None)

  private val A = SpecName("001-a")
  private val B = SpecName("002-b")

  private val a =
    SpecPlan(
      A,
      Plan(
        List(
          ob("rule-success.R", "R"),
          ob("rule-failure.R.1", "R"),
          ob("rule-success.Old", "Old"),
          ob("rule-failure.Old.1", "Old"),
          ob("value-equality.V", "V"),
          ob("surface-exposure.S", "S"),
        ),
        Nil,
      ),
    )

  private val b = SpecPlan(B, Plan(List(ob("rule-success.New", "New"), ob("rule-failure.New.1", "New")), Nil))

  private def ref(spec: SpecName, construct: String) = ConstructRef(spec, construct)

  private val oldByB = Supersession(ref(A, "Old"), List(ref(B, "New")), 1)

  private def tag(spec: SpecName, id: String): Tag = Tag(spec, ObligationId(id), s"features/$spec/x.feature:3")

  private def run(
      tags: List[Tag],
      outOfScope: List[OutOfScope] = Nil,
      superseded: List[Supersession] = List(oldByB),
      supersededInSpecs: Set[ConstructRef] = Set(ref(A, "Old")),
      plans: List[SpecPlan] = List(a, b),
  ): Reconciliation = Coverage.reconcile(plans, tags, outOfScope, superseded, supersededInSpecs)

  private val allTagged =
    List(
      tag(A, "rule-success.R"),
      tag(A, "rule-failure.R.1"),
      tag(A, "value-equality.V"),
      tag(A, "surface-exposure.S"),
      tag(B, "rule-success.New"),
      tag(B, "rule-failure.New.1"),
    )

  private def statusOf(r: Reconciliation, spec: SpecName, id: String): Status =
    r.reports.find(_.spec == spec).flatMap(_.statuses.collectFirst { case (o, s) if o.id.value == id => s }).get

  test("every tagged obligation is covered; superseded ones need no tag") {
    val r = run(tags = allTagged)
    assertEquals(r.problems, Nil)
    assertEquals(statusOf(r, A, "rule-failure.R.1"), Status.Covered)
    assertEquals(statusOf(r, A, "rule-failure.Old.1"), Status.Superseded(List(ref(B, "New"))))
    assertEquals(
      r.reports.map(_.line),
      List(
        "001-a: 6 obligations, 4 covered, 2 uncovered (out of scope: 0, superseded: 2, missing: 0)",
        "002-b: 2 obligations, 2 covered, 0 uncovered (out of scope: 0, superseded: 0, missing: 0)",
      ),
    )
    assertEquals(
      r.totalLine,
      "total: 8 obligations, 6 covered, 2 uncovered (out of scope: 0, superseded: 2, missing: 0)",
    )
  }

  test("an untagged in-scope obligation is missing, with the spec snippet in the message") {
    val source = "rule R {\n    requires: x != null -- not null\n}\n".getBytes(UTF_8)
    val withSpan = a.copy(
      plan = a.plan.copy(obligations = a.plan.obligations.map {
        case o if o.id.value == "rule-failure.R.1" => o.copy(span = Some(Span(13, 44)))
        case o                                     => o
      }),
      source = source,
    )
    val r = run(tags = allTagged.filterNot(_.id.value == "rule-failure.R.1"), plans = List(withSpan, b))
    assertEquals(statusOf(r, A, "rule-failure.R.1"), Status.Missing)
    assertEquals(
      r.problems,
      List(
        "specs/001-a.allium: rule-failure.R.1 — verify rule-failure.R.1 [requires: x != null]: " +
          "no test in features/001-a/ carries @obligation:rule-failure.R.1",
      ),
    )
  }

  test("a tag of another spec does not cover the obligation") {
    val r = run(tags = allTagged.filterNot(_.id.value == "rule-failure.R.1") :+ tag(B, "rule-failure.R.1"))
    assertEquals(statusOf(r, A, "rule-failure.R.1"), Status.Missing)
    assert(r.problems.exists(p => p.contains("features/002-b/x.feature:3") && p.contains("not an obligation of")))
  }

  test("a tag naming an unknown id, a spec without a plan, or a superseded obligation is a problem") {
    val r = run(tags =
      allTagged ++ List(
        tag(A, "rule-failure.R.2"),
        tag(SpecName("009-z"), "rule-success.R"),
        tag(A, "rule-failure.Old.1"),
      ),
    )
    assertEquals(r.problems.size, 3, r.problems)
    assert(r.problems.exists(_.contains("@obligation:rule-failure.R.2 is not an obligation of specs/001-a.allium")))
    assert(r.problems.exists(_.contains("there is no spec specs/009-z.allium")))
    assert(r.problems.exists(_.contains("belongs to the superseded Old")))
  }

  test("a plan with an error diagnostic or a duplicate id is a problem") {
    val broken = SpecPlan(
      SpecName("003-c"),
      Plan(
        List(ob("x.R", "R"), ob("x.R", "R")),
        List(Diagnostic(None, Severity.Error, "boom", None, None)),
      ),
    )
    val r = run(tags = allTagged, plans = List(a, b, broken))
    assert(r.problems.contains("specs/003-c.allium: allium plan: boom"), r.problems)
    assert(r.problems.contains("specs/003-c.allium: duplicate obligation id x.R"), r.problems)
  }

  test("out of scope by id or by kind, for one spec or for every spec") {
    val tags = allTagged.filterNot(t => t.id.value == "value-equality.V" || t.id.value == "surface-exposure.S")
    val r = run(
      tags = tags,
      outOfScope = List(
        OutOfScope(Some(A), Selector.Id(ObligationId("value-equality.V")), "case class", 1),
        OutOfScope(None, Selector.Kind("surface-exposure"), "not rendered", 2),
      ),
    )
    assertEquals(r.problems, Nil)
    assertEquals(statusOf(r, A, "value-equality.V"), Status.OutOfScope("case class"))
    assertEquals(statusOf(r, A, "surface-exposure.S"), Status.OutOfScope("not rendered"))
    assertEquals(r.reports.head.outOfScope, 2)
  }

  test("a stale or covered out-of-scope id entry is a problem") {
    val r = run(
      tags = allTagged,
      outOfScope = List(
        OutOfScope(Some(A), Selector.Id(ObligationId("value-equality.V")), "case class", 4),
        OutOfScope(Some(A), Selector.Id(ObligationId("value-equality.Gone")), "case class", 5),
        OutOfScope(Some(SpecName("009-z")), Selector.Kind("value-equality"), "case class", 6),
      ),
    )
    assertEquals(r.problems.size, 3, r.problems)
    assert(r.problems.exists(_.contains("out-of-scope.txt:4: 001-a/value-equality.V is covered by a test")))
    assert(r.problems.exists(_.contains("out-of-scope.txt:5: 001-a has no obligation value-equality.Gone")))
    assert(r.problems.exists(_.contains("out-of-scope.txt:6: there is no spec")))
  }

  test("the superseded mapping must match the markers and name later, live rules") {
    val unmapped = run(tags = allTagged, superseded = Nil)
    assertEquals(statusOf(unmapped, A, "rule-failure.Old.1"), Status.Missing)
    assert(unmapped.problems.exists(_.contains("Old is marked superseded: add `001-a/Old")))

    val unmarked = run(tags = allTagged, supersededInSpecs = Set.empty)
    assertEquals(
      unmarked.problems,
      List("specs/plan/superseded.txt:1: Old is not marked superseded in specs/001-a.allium"),
    )

    val p = run(
      tags = allTagged,
      superseded = List(
        Supersession(ref(A, "Old"), List(ref(B, "Nope"), ref(A, "R")), 1),
        Supersession(ref(B, "New"), List(ref(SpecName("003-c"), "Newer")), 2),
        Supersession(ref(A, "Gone"), List(ref(B, "New")), 3),
      ),
      supersededInSpecs = Set(ref(A, "Old"), ref(B, "New"), ref(A, "Gone")),
    ).problems
    assert(p.exists(_.contains("superseded.txt:1: 002-b/Nope is not a rule of specs/002-b.allium")), p)
    assert(p.exists(_.contains("superseded.txt:1: 001-a/R cannot supersede 001-a/Old: it is not in a later spec")), p)
    assert(p.exists(_.contains("superseded.txt:3: 002-b/New is itself superseded")), p)
    assert(p.exists(_.contains("superseded.txt:2: 003-c/Newer is not a rule of specs/003-c.allium")), p)
    assert(p.exists(_.contains("superseded.txt:3: specs/001-a.allium has no obligations for Gone")), p)
  }

  test("the layout, spec order and tag prefix are configurable") {
    val settings = Settings(
      layout = Layout.default.copy(specFile = s => s"spec/$s.allium", testsOf = s => s"src/test/$s/"),
      specOrder = SpecOrder.any,
      tagPrefix = "@ob:",
    )
    val r = Coverage.reconcile(
      List(SpecPlan(SpecName("x"), Plan(List(ob("rule-success.R", "R")), Nil))),
      Nil,
      Nil,
      Nil,
      Set.empty,
      settings,
    )
    assertEquals(
      r.problems,
      List("spec/x.allium: rule-success.R — verify rule-success.R: no test in src/test/x/ carries @ob:rule-success.R"),
    )
    assert(SpecOrder.any.isLater(SpecName("a"), SpecName("b")))
    assert(!SpecOrder.numericPrefix.isLater(SpecName("a"), SpecName("001-b")))
    assert(SpecOrder.numericPrefix.isLater(SpecName("010-a"), SpecName("009-b")))
  }

}

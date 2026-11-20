package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

/** The `allium plan` of one spec, with the spec's source for the obligation snippets in messages. */
final case class SpecPlan(spec: SpecName, plan: Plan, source: Array[Byte] = Array.emptyByteArray) {
  lazy val byId: Map[ObligationId, Obligation] = plan.obligations.map(o => o.id -> o).toMap
  lazy val constructs: Set[String] = plan.obligations.map(_.construct).toSet

  def show(o: Obligation): String =
    s"${o.id} — ${o.description}" + o.span.flatMap(SourceText.snippet(source, _)).fold("")(t => s" [$t]")
}

/** A reference from a test to an obligation, for example the `@obligation:<id>` tag of a Gherkin scenario.
  *
  * @param where
  *   where the test is, for messages (`path:line`)
  */
final case class Tag(spec: SpecName, id: ObligationId, where: String)

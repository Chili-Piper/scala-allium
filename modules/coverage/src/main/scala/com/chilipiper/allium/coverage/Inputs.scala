package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

/** The `allium plan` of one spec. */
final case class SpecPlan(spec: SpecName, plan: Plan) {
  lazy val byId: Map[ObligationId, Obligation] = plan.obligations.map(o => o.id -> o).toMap
  lazy val constructs: Set[String] = plan.obligations.map(_.construct).toSet

  def show(o: Obligation): String =
    s"${o.id} — ${o.description}" + o.expression.fold("")(e => s" [${SpecPlan.shorten(e)}]")
}

object SpecPlan {
  private val MaxLength = 160

  private def shorten(text: String): String =
    if (text.length <= MaxLength) text else text.take(MaxLength - 1) + "…"
}

/** A reference from a test to an obligation, for example the `@obligation:<id>` tag of a Gherkin scenario.
  *
  * @param where
  *   where the test is, for messages (`path:line`)
  */
final case class Tag(spec: SpecName, id: ObligationId, where: String)

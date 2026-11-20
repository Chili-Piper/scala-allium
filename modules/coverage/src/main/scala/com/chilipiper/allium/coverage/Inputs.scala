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

/** A spec construct, written `<spec>/<Construct>`. */
final case class ConstructRef(spec: SpecName, construct: String) {
  def show: String = s"$spec/$construct"
}

object ConstructRef {
  implicit val ordering: Ordering[ConstructRef] = Ordering.by(r => (r.spec.value, r.construct))
}

sealed trait Selector extends Product with Serializable {
  def matches(o: Obligation): Boolean =
    this match {
      case Selector.Id(id)     => o.id == id
      case Selector.Kind(kind) => o.kind == kind
    }
}

object Selector {
  final case class Id(id: ObligationId) extends Selector

  /** Every obligation whose id starts with `<kind>.`, written `category:<kind>`. */
  final case class Kind(kind: String) extends Selector
}

/** Obligations that are out of scope by decision. `spec` is `None` for every spec (only with a [[Selector.Kind]]). */
final case class OutOfScope(spec: Option[SpecName], selector: Selector, reason: String, line: Int)

/** `construct` is superseded by the rules `by` of later specs. */
final case class Supersession(construct: ConstructRef, by: List[ConstructRef], line: Int)

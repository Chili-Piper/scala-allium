package com.chilipiper.allium.model

/** The id of an obligation, for example `rule-failure.RefreshAccessToken.3`. */
final case class ObligationId(value: String) extends AnyVal {

  /** The id up to its first dot: `rule-failure` for `rule-failure.RefreshAccessToken.3`. */
  def kind: String = value.takeWhile(_ != '.')

  override def toString: String = value
}

object ObligationId {
  implicit val ordering: Ordering[ObligationId] = Ordering.by(_.value)
}

/** An obligation of `allium plan`: a behaviour of the spec that a test must verify.
  *
  * @param category
  *   as printed by the CLI, for example `rule_failure`
  * @param construct
  *   the spec construct the obligation comes from (`source_construct`), for example a rule name
  */
final case class Obligation(
    id: ObligationId,
    category: String,
    construct: String,
    description: String,
    span: Option[Span],
) {
  def kind: String = id.kind
}

/** The output of `allium plan` for one spec. */
final case class Plan(obligations: List[Obligation], diagnostics: List[Diagnostic])

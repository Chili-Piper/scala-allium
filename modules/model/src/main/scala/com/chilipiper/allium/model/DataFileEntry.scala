package com.chilipiper.allium.model

/** A spec construct, shown as `<spec>/<Construct>`. */
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

  /** Every obligation whose id starts with `<kind>.`. */
  final case class Kind(kind: String) extends Selector
}

/** Obligations that are out of scope by decision. `spec` is `None` for every spec (only with a `Selector.Kind`). */
final case class OutOfScope(spec: Option[SpecName], selector: Selector, reason: String) {

  /** For messages: the spec (`*` for every spec), a slash, then the obligation id or `category:<kind>`. */
  def show: String = {
    val sel = selector match {
      case Selector.Id(id)     => id.value
      case Selector.Kind(kind) => s"category:$kind"
    }
    s"${spec.fold("*")(_.value)}/$sel"
  }
}

/** `construct` is superseded by the rules `by` of later specs. */
final case class Supersession(construct: ConstructRef, by: List[ConstructRef])

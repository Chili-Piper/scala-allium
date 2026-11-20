package com.chilipiper.allium.model

/** The name of a spec: its file name without `.allium` (`001-auth` for `specs/001-auth.allium`). */
final case class SpecName(value: String) extends AnyVal {
  override def toString: String = value
}

object SpecName {
  implicit val ordering: Ordering[SpecName] = Ordering.by(_.value)
}

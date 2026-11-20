package com.chilipiper.allium.coverage

import com.chilipiper.allium.model.SpecName

/** Where things are, for messages. */
final case class Layout(
    specFile: SpecName => String,
    testsOf: SpecName => String,
    outOfScopeFile: String,
    supersededFile: String,
)

object Layout {

  /** `specs/<spec>.allium`, `features/<spec>/`, `specs/plan/out-of-scope.json`, `specs/plan/superseded.json`. */
  val default: Layout = Layout(
    specFile = s => s"specs/$s.allium",
    testsOf = s => s"features/$s/",
    outOfScopeFile = "specs/plan/out-of-scope.json",
    supersededFile = "specs/plan/superseded.json",
  )

}

/** Which specs are later than others: a construct can only be superseded by a rule of a later spec. */
trait SpecOrder {
  def isLater(spec: SpecName, than: SpecName): Boolean
}

object SpecOrder {

  /** By the number the spec name starts with: `002-b` is later than `001-a`. A name without a number is never later. */
  val numericPrefix: SpecOrder = new SpecOrder {
    private def number(s: SpecName): Option[Int] = s.value.takeWhile(_.isDigit).toIntOption
    def isLater(spec: SpecName, than: SpecName): Boolean =
      number(spec).exists(n => number(than).exists(n > _))
  }

  /** Any other spec counts as later. */
  val any: SpecOrder = (spec: SpecName, than: SpecName) => spec != than

}

final case class Settings(
    layout: Layout = Layout.default,
    specOrder: SpecOrder = SpecOrder.numericPrefix,
    tagPrefix: String = Settings.DefaultTagPrefix,
)

object Settings {
  val DefaultTagPrefix: String = "@obligation:"
  val default: Settings = Settings()
}

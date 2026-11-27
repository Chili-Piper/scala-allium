package com.chilipiper.allium.munit

import com.chilipiper.allium.coverage.Tag
import com.chilipiper.allium.model.{ObligationId, SpecName}

/** Reads the obligations that munit tests cover, so unit and property tests count toward the gate.
  *
  * A test covers an obligation of `spec` when it carries the tag `obligation:<id>`:
  * {{{
  * test("hash is lowercase hex".tag(new munit.Tag("obligation:contract-invariant.CryptoOps.HashDefinition"))) { … }
  * }}}
  * The tags come from `munitTests()` (registered when the suite is built), not from the source; the tests do not run.
  */
object MunitCoverage {

  val DefaultPrefix: String = "obligation:"

  /** The tags of the tests of `suite`, as obligations of `spec`. */
  def tags(spec: SpecName, suite: _root_.munit.Suite, prefix: String = DefaultPrefix): List[Tag] =
    suite.munitTests().toList.flatMap { t =>
      t.tags.toList.map(_.value).sorted.collect {
        case name if name.startsWith(prefix) =>
          Tag(spec, ObligationId(name.drop(prefix.length)), s"${suite.getClass.getName}: ${t.name}")
      }
    }

}

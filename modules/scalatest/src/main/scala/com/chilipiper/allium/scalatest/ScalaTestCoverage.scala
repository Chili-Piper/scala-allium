package com.chilipiper.allium.scalatest

import com.chilipiper.allium.coverage.Tag
import com.chilipiper.allium.model.{ObligationId, SpecName}
import org.scalatest.Suite

/** Reads the obligations that ScalaTest tests cover, so unit and property tests count toward the gate.
  *
  * A test covers an obligation of `spec` when it carries the tag `obligation:<id>`:
  * {{{
  * test("hash is lowercase hex", Tag("obligation:contract-invariant.CryptoOps.HashDefinition")) { … }
  * }}}
  * The tags come from `Suite.tags` (registered when the suite is built), not from the source; the tests do not run.
  */
object ScalaTestCoverage {

  val DefaultPrefix: String = "obligation:"

  /** The tags of the tests of `suite` and of its nested suites, as obligations of `spec`. */
  def tags(spec: SpecName, suite: Suite, prefix: String = DefaultPrefix): List[Tag] =
    all(suite).flatMap { s =>
      s.tags.toList.sortBy(_._1).flatMap { case (test, names) =>
        names.toList.sorted.collect {
          case name if name.startsWith(prefix) =>
            Tag(spec, ObligationId(name.drop(prefix.length)), s"${s.suiteName}: $test")
        }
      }
    }

  private def all(suite: Suite): List[Suite] = suite :: suite.nestedSuites.toList.flatMap(all)

}

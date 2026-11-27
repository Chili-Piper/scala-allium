package com.chilipiper.allium.scalatest

import com.chilipiper.allium.coverage.Tag
import com.chilipiper.allium.model.{ObligationId, SpecName}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{Suites, Tag => STag}

class ScalaTestCoverageSuite extends munit.FunSuite {

  private val spec = SpecName("001-a")

  private class Hashing extends AnyFunSuite {
    test("hash is lowercase hex", STag("obligation:contract-invariant.CryptoOps.HashDefinition"), STag("slow")) {
      fail("never runs: only the tags are read")
    }
    test("untagged")(())
  }

  private class Rules extends AnyFunSuite {
    test("revoked token", STag("obligation:rule-failure.Refresh.3"), STag("obligation:rule-failure.Refresh.4"))(())
  }

  test("an obligation:<id> tag of a test is a Tag of the spec; other tags are ignored and nothing runs") {
    val suite = new Hashing
    assertEquals(
      ScalaTestCoverage.tags(spec, suite),
      List(
        Tag(
          spec,
          ObligationId("contract-invariant.CryptoOps.HashDefinition"),
          s"${suite.suiteName}: hash is lowercase hex",
        ),
      ),
    )
  }

  test("several tags of one test, and nested suites, all count") {
    assertEquals(
      ScalaTestCoverage.tags(spec, new Suites(new Hashing, new Rules)).map(_.id.value),
      List(
        "contract-invariant.CryptoOps.HashDefinition",
        "rule-failure.Refresh.3",
        "rule-failure.Refresh.4",
      ),
    )
  }

  test("another prefix") {
    class Custom extends AnyFunSuite { test("t", STag("covers:rule-success.R"))(()) }
    assertEquals(ScalaTestCoverage.tags(spec, new Custom, "covers:").map(_.id.value), List("rule-success.R"))
  }
}

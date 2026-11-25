package com.chilipiper.allium.munit

import com.chilipiper.allium.coverage.Tag
import com.chilipiper.allium.model.{ObligationId, SpecName}

class MunitCoverageSuite extends _root_.munit.FunSuite {

  private val spec = SpecName("001-a")

  private class Hashing extends _root_.munit.FunSuite {
    test(
      "hash is lowercase hex"
        .tag(new _root_.munit.Tag("obligation:contract-invariant.CryptoOps.HashDefinition"))
        .tag(new _root_.munit.Tag("slow")),
    ) {
      fail("never runs: only the tags are read")
    }
    test("revoked token".tag(new _root_.munit.Tag("obligation:rule-failure.Refresh.3")))(())
    test("untagged")(())
  }

  test("an obligation:<id> tag of a test is a Tag of the spec; other tags are ignored and nothing runs") {
    val suite = new Hashing
    assertEquals(
      MunitCoverage.tags(spec, suite),
      List(
        Tag(
          spec,
          ObligationId("contract-invariant.CryptoOps.HashDefinition"),
          s"${suite.getClass.getName}: hash is lowercase hex",
        ),
        Tag(spec, ObligationId("rule-failure.Refresh.3"), s"${suite.getClass.getName}: revoked token"),
      ),
    )
  }

  test("another prefix") {
    class Custom extends _root_.munit.FunSuite { test("t".tag(new _root_.munit.Tag("covers:rule-success.R")))(()) }
    assertEquals(MunitCoverage.tags(spec, new Custom, "covers:").map(_.id.value), List("rule-success.R"))
  }
}

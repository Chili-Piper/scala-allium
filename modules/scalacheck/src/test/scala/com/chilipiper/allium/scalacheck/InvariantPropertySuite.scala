package com.chilipiper.allium.scalacheck

import org.scalacheck.{Gen, Prop, Test}

object InvariantPropertySuite {

  /** An operation on a stock that must never go below zero. */
  sealed trait Op
  final case class Add(n: Int) extends Op
  final case class Take(n: Int) extends Op
}

class InvariantPropertySuite extends munit.FunSuite {

  import InvariantPropertySuite._

  /** `Take` is generated only when there is stock to take, unless `guarded` is false. */
  private def ops(guarded: Boolean)(stock: Int): Gen[Op] =
    if (guarded && stock == 0) Gen.choose(1, 5).map(Add(_))
    else Gen.oneOf(Gen.choose(1, 5).map(Add(_)), Gen.choose(1, if (guarded) stock else 5).map(Take(_)))

  private def step(stock: Int, op: Op): Int = op match {
    case Add(n)  => stock + n
    case Take(n) => stock - n
  }

  private def result(p: Prop): Test.Result = Test.check(Test.Parameters.default.withMinSuccessfulTests(200), p)

  test("a rule set that keeps the invariant passes") {
    val p = InvariantProperty("NonNegativeStock")(Gen.const(0), ops(guarded = true), step)(_ >= 0)
    assert(result(p).passed)
  }

  test("a rule that breaks the invariant fails, naming the invariant, the step and the operation") {
    val p = InvariantProperty("NonNegativeStock")(Gen.const(0), ops(guarded = false), step)(_ >= 0)
    val r = result(p)
    assert(!r.passed)
    val labels = r.status match {
      case Test.Failed(_, ls) => ls
      case other              => fail(s"expected a failure, got $other")
    }
    assert(
      labels.exists(l => l.startsWith("invariant NonNegativeStock broken by step ") && l.contains("Take(")),
      labels,
    )
  }

  test("the invariant is checked on the initial state too") {
    val p = InvariantProperty("NonNegativeStock", maxSteps = 0)(Gen.const(-1), ops(guarded = true), step)(_ >= 0)
    val r = result(p)
    val labels = r.status match {
      case Test.Failed(_, ls) => ls
      case other              => fail(s"expected a failure, got $other")
    }
    assertEquals(labels, Set("invariant NonNegativeStock does not hold on the initial state -1"))
  }
}

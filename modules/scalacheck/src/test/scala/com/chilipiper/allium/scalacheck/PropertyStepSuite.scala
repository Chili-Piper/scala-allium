package com.chilipiper.allium.scalacheck

import org.scalacheck.Prop.propBoolean
import org.scalacheck.{Gen, Prop}

class PropertyStepSuite extends munit.FunSuite {

  test("a property that holds is Right") {
    assertEquals(PropertyStep.check("abs", Prop.forAll(Gen.choose(-100, 100))(i => math.abs(i) >= 0)), Right(()))
  }

  test("a property that fails is Left with its name, the counterexample and the labels") {
    val result = PropertyStep.check("small", Prop.forAll(Gen.choose(0, 100))(i => (i < 50) :| s"i = $i"))
    result match {
      case Left(message) =>
        assert(message.startsWith("small: Falsified"), message)
        assert(message.contains("i = "), message)
      case Right(()) => fail("expected a failure")
    }
  }

  test("the number of cases is the caller's") {
    val counted = new java.util.concurrent.atomic.AtomicInteger(0)
    assertEquals(
      PropertyStep.check("count", Prop.forAll(Gen.const(1)) { _ => counted.incrementAndGet(); true }, 7),
      Right(()),
    )
    assertEquals(counted.get, 7)
  }
}

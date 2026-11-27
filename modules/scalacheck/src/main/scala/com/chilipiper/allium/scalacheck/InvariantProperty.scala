package com.chilipiper.allium.scalacheck

import org.scalacheck.{Gen, Prop}

/** A stateful property for a top-level Allium `invariant`: the invariant must hold after every state-changing rule, so
  * one example is not enough. The property generates an initial state and a random sequence of operations, each
  * generated from the state the previous one produced, and checks the invariant on the initial state and after every
  * step.
  *
  * {{{
  * test("invariant NonNegativeStock", Tag("obligation:invariant.NonNegativeStock")) {
  *   check(InvariantProperty("NonNegativeStock")(Gen.const(Stock.empty), Stock.operations, Stock.apply)(_.count >= 0))
  * }
  * }}}
  *
  * Operations depend on the state, so a shorter sequence may not be valid: the property does not shrink.
  *
  * Run it in memory: `step` applies the pure rules. Against a real database each case is a round trip, so a run gets
  * few cases and is slow. An invariant that only the database enforces (a unique index) needs one slow test, not this
  * property.
  */
object InvariantProperty {

  /** @param name
    *   the invariant's name in the spec, for the failure message
    * @param initial
    *   the state before any operation; the invariant must hold on it too
    * @param operation
    *   the operations that are possible in a state (each a rule that changes state)
    * @param step
    *   the state after an operation
    * @param maxSteps
    *   the longest sequence of operations
    * @param holds
    *   the invariant
    */
  def apply[S, Op](
      name: String,
      maxSteps: Int = 30,
  )(initial: Gen[S], operation: S => Gen[Op], step: (S, Op) => S)(holds: S => Boolean): Prop = {
    def steps(s: S, n: Int): Gen[List[Op]] =
      if (n <= 0) Gen.const(Nil) else operation(s).flatMap(op => steps(step(s, op), n - 1).map(op :: _))

    val run: Gen[(S, List[Op])] =
      for {
        s0 <- initial
        n <- Gen.choose(0, maxSteps)
        ops <- steps(s0, n)
      } yield (s0, ops)

    Prop.forAllNoShrink(run) { case (s0, ops) => check(name, s0, ops, step, holds) }
  }

  private def check[S, Op](name: String, s0: S, ops: List[Op], step: (S, Op) => S, holds: S => Boolean): Prop = {
    val states = ops.scanLeft(s0)(step)
    states.zipWithIndex.find { case (s, _) => !holds(s) } match {
      case None         => Prop.passed
      case Some((s, 0)) => Prop.falsified :| s"invariant $name does not hold on the initial state $s"
      case Some((s, i)) =>
        Prop.falsified :|
          s"invariant $name broken by step $i (${ops(i - 1)}) after ${ops.take(i - 1).mkString("[", ", ", "]")}; state: $s"
    }
  }

}

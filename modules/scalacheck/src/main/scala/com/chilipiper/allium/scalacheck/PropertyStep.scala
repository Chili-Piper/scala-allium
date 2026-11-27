package com.chilipiper.allium.scalacheck

import org.scalacheck.util.Pretty
import org.scalacheck.{Prop, Test}

/** Runs a ScalaCheck property inside a test that is not a ScalaCheck test: a Cucumber step ("for any …"), a ScalaTest
  * or munit test. The verdict is a value; the caller decides how to fail.
  *
  * {{{
  * Then("""it is a Super Admin's, an ordinary user's, or manages at least one workspace""") { () =>
  *   PropertyStep.check("WorkspaceManagerGrantsHaveWorkspaces", sessions)(holds).left.foreach(fail(_))
  * }
  * }}}
  */
object PropertyStep {

  /** Cases per property when the caller does not say: in memory, many cases are cheap. */
  val DefaultMinSuccessfulTests: Int = 500

  /** `Right(())` when the property passed, else `Left` with the name, the counterexample and its labels. */
  def check(name: String, prop: Prop, minSuccessfulTests: Int = DefaultMinSuccessfulTests): Either[String, Unit] = {
    val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(minSuccessfulTests), prop)
    Either.cond(result.passed, (), s"$name: ${Pretty.pretty(result, Pretty.Params(1))}")
  }

}

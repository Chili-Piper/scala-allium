package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

/** Obligations for the named annotations that `allium plan` skips, read from `allium parse`:
  *
  *   - `@guarantee G` in `surface S`: `surface-guarantee.S.G` (category `surface_guarantee`);
  *   - `@invariant I` in `contract C`: `contract-invariant.C.I` (category `contract_invariant`).
  *
  * The ids, categories and constructs (`S.G`, `C.I`) are the ones of `allium plan` in
  * https://github.com/juxt/allium-tools/pull/139. [[add]] keeps an obligation the plan already has, so a CLI that emits
  * them makes this a no-op without changing any tag.
  */
object AnnotationObligations {

  /** The obligations of the guarantees of the surfaces and the invariants of the contracts of `outline`. */
  def of(outline: Outline): List[Obligation] =
    outline.declarations.flatMap { d =>
      d.name.toList.flatMap { block =>
        d.annotations.flatMap { a =>
          (d.kind, a.kind, a.name) match {
            case (DeclarationKind.Surface, AnnotationKind.Guarantee, Some(name)) =>
              List(
                obligation(
                  s"surface-guarantee.$block.$name",
                  "surface_guarantee",
                  s"$block.$name",
                  a,
                  s"Verify surface $block upholds guarantee $name",
                ),
              )
            case (DeclarationKind.Contract, AnnotationKind.Invariant, Some(name)) =>
              List(
                obligation(
                  s"contract-invariant.$block.$name",
                  "contract_invariant",
                  s"$block.$name",
                  a,
                  s"Verify implementations of contract $block uphold invariant $name",
                ),
              )
            case _ => Nil
          }
        }
      }
    }

  /** `plan` with the obligations of [[of]] that it does not have yet. */
  def add(plan: Plan, outline: Outline): Plan = {
    val known = plan.obligations.map(_.id).toSet
    plan.copy(obligations = plan.obligations ++ of(outline).filterNot(o => known.contains(o.id)))
  }

  private def obligation(
      id: String,
      category: String,
      construct: String,
      a: Annotation,
      description: String,
  ): Obligation =
    Obligation(
      ObligationId(id),
      category,
      construct,
      description,
      Some(a.span),
      Some(a.body.mkString(" ")).filter(_.nonEmpty),
    )

}

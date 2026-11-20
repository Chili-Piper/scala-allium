package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

/** Reconciles the obligations of `allium plan` with the tags of the tests.
  *
  * Every obligation ends in exactly one [[Status]]: covered (a test of its spec carries its tag), superseded (its
  * construct is in the superseded file), out of scope (the out-of-scope file) or missing. Missing obligations, tags
  * that name no obligation, and stale data file entries are problems.
  */
object Coverage {

  /** @param supersededInSpecs
    *   every construct a spec marks as superseded (see [[SupersededMarker]])
    */
  def reconcile(
      plans: List[SpecPlan],
      tags: List[Tag],
      outOfScope: List[OutOfScope],
      superseded: List[Supersession],
      supersededInSpecs: Set[ConstructRef],
      settings: Settings = Settings.default,
  ): Reconciliation = {
    val layout = settings.layout
    val prefix = settings.tagPrefix
    val planOf = plans.map(p => p.spec -> p).toMap
    val supersededBy = superseded.map(s => s.construct -> s).toMap

    val planProblems = plans.flatMap { p =>
      val errors = p.plan.diagnostics.filter(_.isError).map(d => s"${layout.specFile(p.spec)}: allium plan: ${d.show}")
      val ids = p.plan.obligations.map(_.id)
      val dupes = ids.diff(ids.distinct).distinct.map(id => s"${layout.specFile(p.spec)}: duplicate obligation id $id")
      errors ++ dupes
    }

    val tagProblems = tags.flatMap { t =>
      planOf.get(t.spec) match {
        case None       => List(s"${t.where}: $prefix${t.id}: there is no spec ${layout.specFile(t.spec)}")
        case Some(plan) =>
          plan.byId.get(t.id) match {
            case None =>
              List(
                s"${t.where}: $prefix${t.id} is not an obligation of ${layout.specFile(t.spec)} " +
                  s"(see `allium plan ${layout.specFile(t.spec)}`; ids embed clause indexes, so a reordered `requires` renumbers them)",
              )
            case Some(o) if supersededBy.contains(ConstructRef(t.spec, o.construct)) =>
              List(
                s"${t.where}: $prefix${t.id} belongs to the superseded ${o.construct}; " +
                  "tag the obligations of the superseding construct instead",
              )
            case Some(_) => Nil
          }
      }
    }
    val tagged: Set[(SpecName, ObligationId)] = tags.map(t => t.spec -> t.id).toSet

    val outOfScopeProblems = outOfScope.flatMap { e =>
      val where = s"${layout.outOfScopeFile}:${e.line}"
      (e.spec, e.selector) match {
        case (Some(spec), _) if !planOf.contains(spec) => List(s"$where: there is no spec ${layout.specFile(spec)}")
        case (Some(spec), Selector.Id(id)) if !planOf(spec).byId.contains(id) =>
          List(s"$where: $spec has no obligation $id: remove the stale entry")
        case (Some(spec), Selector.Id(id)) if tagged.contains(spec -> id) =>
          List(s"$where: $spec/$id is covered by a test: remove it from the out-of-scope list")
        case _ => Nil
      }
    }

    val supersededProblems =
      superseded.flatMap { s =>
        val where = s"${layout.supersededFile}:${s.line}"
        val c = s.construct
        val key =
          if (!planOf.contains(c.spec))
            List(s"$where: there is no spec ${layout.specFile(c.spec)}")
          else if (!planOf(c.spec).constructs.contains(c.construct))
            List(s"$where: ${layout.specFile(c.spec)} has no obligations for ${c.construct}: remove the stale entry")
          else if (!supersededInSpecs.contains(c))
            List(s"$where: ${c.construct} is not marked superseded in ${layout.specFile(c.spec)}")
          else
            Nil
        val successors = s.by.flatMap { b =>
          if (!settings.specOrder.isLater(b.spec, c.spec))
            List(s"$where: ${b.show} cannot supersede ${c.show}: it is not in a later spec")
          else if (!planOf.get(b.spec).exists(_.byId.contains(ObligationId(s"rule-success.${b.construct}"))))
            List(s"$where: ${b.show} is not a rule of ${layout.specFile(b.spec)} (no rule-success.${b.construct})")
          else if (supersededBy.contains(b))
            List(s"$where: ${b.show} is itself superseded: name what supersedes it")
          else
            Nil
        }
        key ++ successors
      } ++
        supersededInSpecs.toList.sorted.collect {
          case c if planOf.get(c.spec).exists(_.constructs.contains(c.construct)) && !supersededBy.contains(c) =>
            s"${layout.specFile(c.spec)}: ${c.construct} is marked superseded: add `${c.show} <superseding spec>/<rule>` " +
              s"to ${layout.supersededFile}"
        }

    val reports = plans.sortBy(_.spec).map { plan =>
      SpecReport(
        plan.spec,
        plan.plan.obligations.map { o =>
          val status: Status =
            if (tagged.contains(plan.spec -> o.id))
              Status.Covered
            else
              supersededBy.get(ConstructRef(plan.spec, o.construct)) match {
                case Some(s) => Status.Superseded(s.by)
                case None    =>
                  outOfScope.find(e => e.spec.forall(_ == plan.spec) && e.selector.matches(o)) match {
                    case Some(e) => Status.OutOfScope(e.reason)
                    case None    => Status.Missing
                  }
              }
          o -> status
        },
      )
    }

    val missing = reports.flatMap { r =>
      val plan = planOf(r.spec)
      r.statuses.collect { case (o, Status.Missing) =>
        s"${layout.specFile(r.spec)}: ${plan.show(o)}: no test in ${layout.testsOf(r.spec)} carries $prefix${o.id}"
      }
    }
    Reconciliation(reports, planProblems ++ tagProblems ++ outOfScopeProblems ++ supersededProblems ++ missing)
  }

}

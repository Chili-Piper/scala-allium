package com.chilipiper.allium.coverage

import com.chilipiper.allium.model.{ConstructRef, Obligation, SpecName}

/** Each obligation ends in exactly one status. */
sealed trait Status extends Product with Serializable

object Status {
  case object Covered extends Status
  final case class OutOfScope(reason: String) extends Status
  final case class Superseded(by: List[ConstructRef]) extends Status

  /** A known gap, listed in the pending file: reported, not a problem. */
  final case class Pending(reason: String) extends Status
  case object Missing extends Status
}

final case class SpecReport(spec: SpecName, statuses: List[(Obligation, Status)]) {

  private def count(p: Status => Boolean): Int = statuses.count { case (_, s) => p(s) }

  def total: Int = statuses.size
  def covered: Int = count(_ == Status.Covered)
  def outOfScope: Int = count(_.isInstanceOf[Status.OutOfScope])
  def superseded: Int = count(_.isInstanceOf[Status.Superseded])
  def pending: Int = count(_.isInstanceOf[Status.Pending])
  def missing: Int = count(_ == Status.Missing)

  def line: String = SpecReport.line(spec.value, total, covered, outOfScope, superseded, missing, pending)
}

object SpecReport {

  /** `pending` is shown only when there is one, so the line of a gate without a pending file does not change. */
  def line(
      label: String,
      total: Int,
      covered: Int,
      outOfScope: Int,
      superseded: Int,
      missing: Int,
      pending: Int = 0,
  ): String =
    s"$label: $total obligations, $covered covered, ${total - covered} uncovered " +
      s"(out of scope: $outOfScope, superseded: $superseded, " +
      (if (pending > 0) s"pending: $pending, " else "") +
      s"missing: $missing)"

}

/** The result of `Coverage.reconcile`. The gate passes when `problems` is empty. */
final case class Reconciliation(reports: List[SpecReport], problems: List[String]) {

  def totalLine: String =
    SpecReport.line(
      "total",
      reports.map(_.total).sum,
      reports.map(_.covered).sum,
      reports.map(_.outOfScope).sum,
      reports.map(_.superseded).sum,
      reports.map(_.missing).sum,
      reports.map(_.pending).sum,
    )

}

package com.chilipiper.allium.model

sealed trait Severity extends Product with Serializable

object Severity {
  case object Error extends Severity
  case object Warning extends Severity
  case object Info extends Severity
  case object Hint extends Severity

  /** A severity this library does not know, as printed by the CLI. */
  final case class Other(value: String) extends Severity

  /** Case-insensitive: `allium check` prints `error`, `allium parse` prints `Error`. */
  def parse(value: String): Severity =
    value.toLowerCase match {
      case "error"   => Error
      case "warning" => Warning
      case "info"    => Info
      case "hint"    => Hint
      case _         => Other(value)
    }
}

/** A 1-based position in a spec file (`allium check`). */
final case class Location(file: Option[String], line: Option[Int], column: Option[Int])

/** A diagnostic of the `allium` CLI. `check` gives a [[Location]], `parse` and `plan` give a [[Span]]. */
final case class Diagnostic(
    code: Option[String],
    severity: Severity,
    message: String,
    location: Option[Location],
    span: Option[Span],
) {
  def isError: Boolean = severity == Severity.Error

  /** `file:line: code: message`, with the parts that are known. */
  def show: String = {
    val where = location.toList.flatMap(l => l.file.toList ++ l.line.map(_.toString).toList).mkString(":")
    (List(where).filter(_.nonEmpty) ++ code.toList :+ message).mkString(": ")
  }
}

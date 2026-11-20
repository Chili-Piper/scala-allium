package com.chilipiper.allium.model

/** The top-level declarations of a spec, read from `allium parse`. It keeps names, kinds and spans, not expressions. */
final case class Outline(declarations: List[Declaration], diagnostics: List[Diagnostic])

final case class Declaration(kind: DeclarationKind, name: Option[String], span: Span, annotations: List[Annotation])

sealed trait DeclarationKind extends Product with Serializable

object DeclarationKind {
  case object Rule extends DeclarationKind
  case object Invariant extends DeclarationKind
  case object Surface extends DeclarationKind
  case object Contract extends DeclarationKind
  case object Entity extends DeclarationKind
  case object Config extends DeclarationKind
  case object Use extends DeclarationKind

  /** A kind this library does not model, as printed by the CLI (`Value`, `Enum`, …). */
  final case class Other(value: String) extends DeclarationKind
}

/** An annotation of a block, for example `@invariant UniqueHash` or `@guarantee G`.
  *
  * @param body
  *   the comment lines under the annotation, without `--`
  */
final case class Annotation(kind: AnnotationKind, name: Option[String], body: List[String], span: Span)

sealed trait AnnotationKind extends Product with Serializable

object AnnotationKind {
  case object Invariant extends AnnotationKind
  case object Guarantee extends AnnotationKind
  final case class Other(value: String) extends AnnotationKind
}

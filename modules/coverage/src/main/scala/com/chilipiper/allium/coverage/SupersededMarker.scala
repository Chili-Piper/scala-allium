package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

import scala.util.matching.Regex

/** Finds the constructs (rules and invariants) that a spec marks as superseded. The AST of `allium parse` has no
  * comments, so a marker is read from the source text at the AST's spans.
  */
trait SupersededMarker {
  def superseded(source: Array[Byte], outline: Outline): Set[String]
}

object SupersededMarker {

  /** No construct is ever marked. */
  val none: SupersededMarker = (_: Array[Byte], _: Outline) => Set.empty

  /** A block is superseded when a line of it matches `blockLine`. An `@invariant` annotation is superseded with its
    * block, or when a line of its comment body matches `annotationLine`.
    */
  def lines(blockLine: Regex, annotationLine: Regex): SupersededMarker =
    (source: Array[Byte], outline: Outline) => {
      def marked(span: Span): Boolean =
        SourceText.of(source, span).exists(_.linesIterator.exists(blockLine.matches))
      outline.declarations.flatMap { d =>
        val block = marked(d.span)
        val own = d.kind match {
          case DeclarationKind.Rule | DeclarationKind.Invariant if block => d.name.toList
          case _                                                         => Nil
        }
        val annotations = d.annotations.collect {
          case Annotation(AnnotationKind.Invariant, Some(n), body, _)
              if block || body.exists(l => annotationLine.matches(l.trim)) =>
            n
        }
        own ++ annotations
      }.toSet
    }

  /** A block-level (4-space indented) `-- @superseded …` comment line, or an `@invariant` whose comment body starts
    * with `@superseded`. Deeper indented markers (a branch of a rule) do not supersede the block.
    */
  val commentLine: SupersededMarker = lines("""^ {4}-- @superseded\b.*$""".r, """^@superseded\b.*$""".r)

}

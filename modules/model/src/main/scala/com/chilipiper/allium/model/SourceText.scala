package com.chilipiper.allium.model

import java.nio.charset.StandardCharsets.UTF_8

object SourceText {

  /** The text of `span` in `source`, or `None` when the span is outside it. */
  def of(source: Array[Byte], span: Span): Option[String] =
    if (span.start < 0 || span.end > source.length || span.start > span.end)
      None
    else
      Some(new String(source, span.start, span.end - span.start, UTF_8))

  /** The text of `span` for messages: comments removed, whitespace collapsed, at most `maxLength` characters. */
  def snippet(source: Array[Byte], span: Span, maxLength: Int = 160): Option[String] =
    of(source, span).map { text =>
      val code = text.linesIterator.map(_.split("--", 2).head).mkString(" ")
      val collapsed = code.replaceAll("\\s+", " ").trim
      if (collapsed.length <= maxLength)
        collapsed
      else
        collapsed.take(maxLength - 1) + "…"
    }

}

package com.chilipiper.allium.model

/** A byte range `[start, end)` of a spec file, as the `allium` CLI reports it. */
final case class Span(start: Int, end: Int)

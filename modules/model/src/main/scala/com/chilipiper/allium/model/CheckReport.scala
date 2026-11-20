package com.chilipiper.allium.model

/** The `allium check` result of one spec file. */
final case class CheckReport(specFile: Option[String], diagnostics: List[Diagnostic])

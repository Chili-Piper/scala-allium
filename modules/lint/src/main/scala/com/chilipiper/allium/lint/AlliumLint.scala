package com.chilipiper.allium.lint

import com.chilipiper.allium.model.{CheckReport, Diagnostic, SpecName}
import io.circe.Json

/** Checks that the `allium` CLI does not make (yet). */
object AlliumLint {

  /** Warning codes that are as fatal as errors: a bare `config.x` that the spec's own config block does not declare,
    * and a `use` path that does not resolve.
    */
  val DefaultFatalCodes: Set[String] = Set("allium.config.undefinedReference", "allium.use.unresolvedPath")

  /** The diagnostics of `allium check` that fail the gate: every error, and every diagnostic with a fatal code. */
  def failing(reports: List[CheckReport], fatalCodes: Set[String] = DefaultFatalCodes): List[Diagnostic] =
    reports.flatMap(_.diagnostics).filter(d => d.isError || d.code.exists(fatalCodes.contains))

  /** Every `alias/config.param` in the `allium parse` AST of `spec` whose `param` the `config` block of the aliased
    * spec does not declare. The CLI checks only the alias (https://github.com/juxt/allium-tools/issues/135).
    *
    * @param astOf
    *   the `allium parse` AST of another spec, by name; a `use` target without an AST is not checked
    * @param specFile
    *   the path of a spec, for messages
    */
  def undeclaredConfigReferences(
      spec: SpecName,
      ast: Json,
      astOf: SpecName => Option[Json],
      specFile: SpecName => String = s => s"specs/$s.allium",
  ): List[String] = {
    def declarations(j: Json): List[Json] =
      j.hcursor.downField("module").downField("declarations").values.toList.flatten
    val aliases: Map[String, SpecName] =
      declarations(ast).flatMap { d =>
        val use = d.hcursor.downField("Use")
        for {
          alias <- use.downField("alias").get[String]("name").toOption
          path <- use.downField("path").downField("parts").downArray.get[String]("Text").toOption
        } yield alias -> SpecName(path.split('/').last.stripSuffix(".allium"))
      }.toMap
    def params(target: SpecName): Option[Set[String]] =
      astOf(target).map { t =>
        declarations(t)
          .map(_.hcursor.downField("Block"))
          .filter(_.get[String]("kind").toOption.contains("Config"))
          .flatMap(_.downField("items").values.toList.flatten)
          .flatMap(_.hcursor.downField("kind").downField("Assignment").downField("name").get[String]("name").toOption)
          .toSet
      }
    def memberAccesses(j: Json): List[Json] =
      j.fold(
        Nil,
        _ => Nil,
        _ => Nil,
        _ => Nil,
        _.toList.flatMap(memberAccesses),
        o => o("MemberAccess").toList ++ o.values.toList.flatMap(memberAccesses),
      )
    memberAccesses(ast).flatMap { m =>
      val c = m.hcursor
      val obj = c.downField("object").downField("QualifiedName")
      for {
        alias <- obj.get[String]("qualifier").toOption
        if obj.get[String]("name").toOption.contains("config")
        param <- c.downField("field").get[String]("name").toOption
        target <- aliases.get(alias)
        declared <- params(target)
        if !declared.contains(param)
      } yield s"${specFile(spec)}: $alias/config.$param is not declared in the config block of ${specFile(target)}"
    }.distinct
  }

}

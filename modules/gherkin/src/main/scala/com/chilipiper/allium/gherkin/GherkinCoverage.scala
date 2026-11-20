package com.chilipiper.allium.gherkin

import com.chilipiper.allium.coverage.{Settings, Tag}
import com.chilipiper.allium.model.{ObligationId, SpecName}

import java.nio.file.Paths

object GherkinCoverage {

  /** The spec of a feature file: the name of the directory it is in (`features/001-auth/x.feature` → `001-auth`). */
  val specOfParentDirectory: Feature => Option[SpecName] =
    f => Option(Paths.get(f.path).getParent).map(p => SpecName(p.getFileName.toString))

  /** The `@obligation:<id>` tags of every scenario, for `Coverage.reconcile`.
    *
    * @param specOf
    *   which spec the scenarios of a feature test; a feature without a spec contributes no tags
    */
  def tags(
      features: List[Feature],
      specOf: Feature => Option[SpecName] = specOfParentDirectory,
      tagPrefix: String = Settings.DefaultTagPrefix,
  ): List[Tag] =
    for {
      f <- features
      spec <- specOf(f).toList
      sc <- f.scenarios
      tag <- sc.tags if tag.startsWith(tagPrefix)
    } yield Tag(spec, ObligationId(tag.stripPrefix(tagPrefix)), s"${f.path}:${sc.line}")

  /** Tags that keep a scenario from running. A tag that names an obligation must not be hidden behind one. */
  val DefaultSkipTags: Set[String] = Set("@wip", "@ignore", "@skip", "@disabled", "@pending")

  /** A problem for every scenario tagged with one of `skipTags` (case-insensitive). */
  def skippedScenarios(features: List[Feature], skipTags: Set[String] = DefaultSkipTags): List[String] = {
    val skip = skipTags.map(_.toLowerCase)
    for {
      f <- features
      sc <- f.scenarios
      tag <- sc.tags if skip.contains(tag.toLowerCase)
    } yield s"${f.path}:${sc.line}: '${sc.name}' is tagged $tag"
  }

}

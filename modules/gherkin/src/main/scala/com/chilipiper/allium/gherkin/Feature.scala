package com.chilipiper.allium.gherkin

import io.cucumber.gherkin.GherkinParser
import io.cucumber.messages.types.{Scenario => GherkinScenario, Tag => GherkinTag}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

/** A scenario (or example, or scenario outline) with every tag it runs with. */
final case class Scenario(name: String, line: Int, tags: List[String])

/** A feature file, read with Cucumber's own Gherkin parser.
  *
  * @param path
  *   the path for messages, usually relative to the repository root
  * @param problems
  *   parse errors; a file Gherkin cannot parse has no name, no scenarios and at least one problem
  */
final case class Feature(path: String, name: String, scenarios: List[Scenario], problems: List[String])

object Feature {

  private val parser = GherkinParser.builder().includeSource(false).includePickles(false).build()

  def read(path: Path, relativeTo: Path): Feature =
    parse(relativeTo.relativize(path).toString, Files.readString(path))

  /** Every scenario carries the tags it runs with: its own, those of the `Feature:` and the enclosing `Rule:`
    * (inherited), and those of its `Examples:` blocks.
    */
  def parse(path: String, text: String): Feature = {
    val envelopes = parser.parse(path, text.getBytes(UTF_8)).iterator().asScala.toList
    val errors = envelopes.flatMap(_.getParseError.toScala).map(e => s"$path: ${e.getMessage}")
    val feature = envelopes.flatMap(_.getGherkinDocument.toScala).flatMap(_.getFeature.toScala).headOption
    def tags(ts: java.util.List[GherkinTag]): List[String] = ts.asScala.toList.map(_.getName)
    def scenario(sc: GherkinScenario, inherited: List[String]): Scenario =
      Scenario(
        sc.getName,
        sc.getLocation.getLine.intValue,
        (tags(sc.getTags) ++ inherited ++ sc.getExamples.asScala.toList.flatMap(e => tags(e.getTags))).distinct,
      )
    feature match {
      case None    => Feature(path, "", Nil, if (errors.isEmpty) List(s"$path: no Feature") else errors)
      case Some(f) =>
        val featureTags = tags(f.getTags)
        val scenarios = f.getChildren.asScala.toList.flatMap { child =>
          child.getScenario.toScala.map(scenario(_, featureTags)).toList ++
            child.getRule.toScala.toList.flatMap { rule =>
              rule.getChildren.asScala.toList
                .flatMap(_.getScenario.toScala)
                .map(scenario(_, featureTags ++ tags(rule.getTags)))
            }
        }
        Feature(path, f.getName, scenarios, errors)
    }
  }

  /** Every `*.feature` file under `dir`, sorted by path. */
  def readAll(dir: Path, relativeTo: Path): List[Feature] =
    if (!Files.isDirectory(dir))
      Nil
    else {
      val stream = Files.walk(dir)
      try
        stream
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".feature"))
          .toList
          .sorted
          .map(read(_, relativeTo))
      finally stream.close()
    }

}

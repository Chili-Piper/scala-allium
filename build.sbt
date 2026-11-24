import org.typelevel.scalacoptions.*

ThisBuild / organization := "com.chilipiper"
ThisBuild / homepage := Some(url("https://github.com/Chili-Piper/scala-allium/"))
ThisBuild / licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer(id = "chilipiper", name = "Chili Piper", email = "", url = url("https://chilipiper.com")),
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/Chili-Piper/scala-allium"),
    "scm:git:https://github.com/Chili-Piper/scala-allium.git",
    Some("scm:git:git@github.com:Chili-Piper/scala-allium.git"),
  ),
)
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / crossScalaVersions := List("2.13.18", "3.3.9")
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val commonSettings = List(
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => List("-Xsource:3", "-Wunused")
      case _            => Nil
    }
  },
  tpolecatDevModeOptions ~= { opts =>
    opts.filterNot(Set(ScalacOptions.warnError))
  },
  libraryDependencies += Dependencies.munit,
)

/** Allium data types: obligations, plans, diagnostics, spec outlines. No dependencies. */
lazy val model = (project in file("modules/model"))
  .settings(commonSettings)
  .settings(name := "allium-model")

/** Circe decoders for the JSON that the `allium` CLI prints (`check`, `plan`, `parse`) and for the data files. */
lazy val circe = (project in file("modules/circe"))
  .dependsOn(model, cli % "test->test", coverage % Test)
  .settings(commonSettings)
  .settings(
    name := "allium-circe",
    libraryDependencies ++= List(Dependencies.circeCore, Dependencies.circeJawn),
  )

/** Downloads, verifies and runs the `allium` CLI. No dependencies. */
lazy val cli = (project in file("modules/cli"))
  .settings(commonSettings)
  .settings(name := "allium-cli")

/** Reconciles `allium plan` obligations with the tests that cover them. Pure, no I/O. */
lazy val coverage = (project in file("modules/coverage"))
  .dependsOn(model)
  .settings(commonSettings)
  .settings(name := "allium-coverage")

/** Reads `@obligation:<id>` tags from Gherkin feature files. */
lazy val gherkin = (project in file("modules/gherkin"))
  .dependsOn(coverage)
  .settings(commonSettings)
  .settings(
    name := "allium-gherkin",
    libraryDependencies += Dependencies.gherkin,
  )

/** Checks that the `allium` CLI does not do yet, on the `allium parse` AST and `allium check` diagnostics. */
lazy val lint = (project in file("modules/lint"))
  .dependsOn(model, circe, cli % "test->test")
  .settings(commonSettings)
  .settings(
    name := "allium-lint",
    libraryDependencies += Dependencies.circeCore,
  )

/** The README example, end to end on a fixture repository with the real CLI. Not published. */
lazy val examples = (project in file("modules/examples"))
  .dependsOn(circe, cli, gherkin, lint)
  .settings(commonSettings)
  .settings(
    name := "allium-examples",
    publish / skip := true,
  )

lazy val root = (project in file("."))
  .aggregate(model, circe, cli, coverage, gherkin, lint, examples)
  .settings(
    name := "scala-allium",
    publish / skip := true,
  )

addCommandAlias(
  "ci",
  "; scalafmtSbtCheck; scalafmtCheckAll; +compile; +test; scalafixAll --check",
)

import sbt._

object Dependencies {

  object Versions {
    val circe = "0.14.17"
    val gherkin = "43.0.0"
    val munit = "1.3.6"
  }

  val circeCore = "io.circe" %% "circe-core" % Versions.circe
  val circeJawn = "io.circe" %% "circe-jawn" % Versions.circe
  val gherkin = "io.cucumber" % "gherkin" % Versions.gherkin
  val munit = "org.scalameta" %% "munit" % Versions.munit % Test

}

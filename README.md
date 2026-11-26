# scala-allium

Scala tools for [Allium](https://github.com/juxt/allium-tools) specifications. The main use: a test that fails when
an obligation of `allium plan` has no test that covers it.

Cross-built for Scala 2.13 and 3.

## Modules

| Module            | Depends on          | What it does                                                                          |
|-------------------|---------------------|---------------------------------------------------------------------------------------|
| `allium-model`    | –                   | Data types: `Obligation`, `Plan`, `Diagnostic`, `Outline`, data file entries          |
| `allium-circe`    | model, circe        | Decodes the JSON of `allium plan/parse/check` and the data files                      |
| `allium-cli`      | –                   | Downloads a pinned CLI release (SHA-256 verified) and runs it                         |
| `allium-coverage` | model               | Reconciles obligations with test tags. Pure, no I/O                                   |
| `allium-gherkin`  | coverage, gherkin   | Reads `@obligation:<id>` tags from `.feature` files; bans skip tags                   |
| `allium-scalatest` | coverage, scalatest | Reads `obligation:<id>` tags from ScalaTest suites (`Suite.tags`)                     |
| `allium-munit`    | coverage, munit     | Reads `obligation:<id>` tags from munit suites (`munitTests()`)                       |
| `allium-scalacheck` | scalacheck        | `InvariantProperty`: checks an Allium `invariant` after every step of random operation sequences |
| `allium-lint`     | model, circe        | Checks the CLI does not make yet: undeclared `alias/config.param`, fatal warning codes |

```scala
libraryDependencies ++= List(
  "com.chilipiper" %% "allium-cli" % version,
  "com.chilipiper" %% "allium-circe" % version,
  "com.chilipiper" %% "allium-gherkin" % version, // brings allium-coverage and allium-model
  "com.chilipiper" %% "allium-lint" % version,
).map(_ % Test)
```

## The coverage gate

Which kind of test fits which Allium construct (BDD, property, unit, boundary, structural): [docs/testing-strategy.md](docs/testing-strategy.md).

Each obligation ends in exactly one status:

- **covered**: a test of its spec carries the tag `@obligation:<id>`;
- **superseded**: its construct is listed in `specs/plan/superseded.json`;
- **out of scope**: it is listed in `specs/plan/out-of-scope.json`;
- **missing**: none of the above. This is a problem.

A tag that names no obligation, and a stale entry in a data file, are problems too.

`allium plan` has no obligation for a surface `@guarantee` or a contract `@invariant`. `AnnotationObligations.add`
derives them from `allium parse`: `surface-guarantee.<Surface>.<Name>` and `contract-invariant.<Contract>.<Name>`
(categories `surface_guarantee`, `contract_invariant`), the ids proposed upstream for `allium plan`. When the CLI emits
them, `add` keeps the CLI's and the tags stay the same. Which kind of test fits them:
[docs/testing-strategy.md](docs/testing-strategy.md).

### Example (munit)

A tested version of this example, with a fixture repository, is
[`ObligationCoverageSuite`](modules/examples/src/test/scala/com/chilipiper/allium/examples/ObligationCoverageSuite.scala).

```scala
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

import com.chilipiper.allium.circe.AlliumJson
import com.chilipiper.allium.cli.{AlliumCli, Release}
import com.chilipiper.allium.coverage.*
import com.chilipiper.allium.gherkin.{Feature, GherkinCoverage}
import com.chilipiper.allium.model.SpecName

class ObligationCoverageSuite extends munit.FunSuite {
  test("every obligation is covered") {
    val root = Paths.get(".").toAbsolutePath.normalize
    val cli = AlliumCli.download(Release.latest, root.resolve("target/allium-cli")).fold(fail(_), identity)
    val specs = Files.list(root.resolve("specs")).iterator.asScala.filter(_.toString.endsWith(".allium")).toList.sorted

    val plans = specs.map { p =>
      val name = SpecName(p.getFileName.toString.stripSuffix(".allium"))
      val file = root.relativize(p).toString
      val plan = cli.plan(root, file).flatMap(AlliumJson.plan).fold(fail(_), identity)
      val outline = cli.parse(root, file).flatMap(AlliumJson.outline).fold(fail(_), identity)
      // Surface @guarantee and contract @invariant: `allium plan` has no obligation for them.
      SpecPlan(name, AnnotationObligations.add(plan, outline))
    }

    val features = Feature.readAll(root.resolve("features"), root)
    def data[A](f: String)(decode: String => Either[String, List[A]]): List[A] =
      Option(root.resolve(f)).filter(Files.exists(_)).toList.flatMap(p => decode(Files.readString(p)).fold(fail(_), identity))
    val outOfScope = data(Layout.default.outOfScopeFile)(AlliumJson.outOfScope)
    val superseded = data(Layout.default.supersededFile)(AlliumJson.superseded)

    val result = Coverage.reconcile(
      plans,
      GherkinCoverage.tags(features),
      outOfScope,
      superseded,
    )
    val problems = features.flatMap(_.problems) ++ GherkinCoverage.skippedScenarios(features) ++ result.problems
    assert(problems.isEmpty, (problems ++ result.reports.map(_.line) :+ result.totalLine).mkString("\n"))
  }
}
```

### Tests other than Gherkin scenarios

Unit, property and integration tests count too: give the test the tag `obligation:<id>` and add its suite to the gate.
The tags are read from the framework's model of the suite; the tests do not run and the source is not read.

```scala
// ScalaTest (allium-scalatest)
test("hash is lowercase hex", Tag("obligation:contract-invariant.CryptoOps.HashDefinition")) { … }
// munit (allium-munit)
test("hash is lowercase hex".tag(new munit.Tag("obligation:contract-invariant.CryptoOps.HashDefinition"))) { … }

val tags =
  GherkinCoverage.tags(features) ++
    ScalaTestCoverage.tags(SpecName("001-auth"), new CryptoOpsSuite) ++
    MunitCoverage.tags(SpecName("001-auth"), new TokenStoreSuite)
```

A top-level `invariant` must hold after every state-changing rule; `allium-scalacheck` checks that on random sequences
of operations, each generated from the current state:

```scala
test("invariant NonNegativeStock", Tag("obligation:invariant.NonNegativeStock")) {
  check(InvariantProperty("NonNegativeStock")(Gen.const(Stock.empty), Stock.operations, Stock.apply)(_.count >= 0))
}
```

A failure names the step and the operation that broke the invariant. Laws for `value-equality` and `enum-comparable`
are in cats-kernel-laws (discipline).

Which kind of test fits which construct: [docs/testing-strategy.md](docs/testing-strategy.md).

### Data files

Both files are JSON arrays, decoded by `AlliumJson.outOfScope` and `AlliumJson.superseded`. An unknown field is an
error, so a typo does not silently change an entry.

`specs/plan/out-of-scope.json`: an obligation of a spec, or a category (the id up to its first dot) of one spec or,
without `spec`, of every spec.

```json
[
  { "spec": "001-auth", "obligation": "value-equality.Token", "reason": "Scala case class: equality is structural" },
  { "category": "surface-exposure", "reason": "no UI" }
]
```

`specs/plan/superseded.json`: a construct, then the rules of later specs that supersede it.

```json
[
  {
    "spec": "001-auth",
    "construct": "Refresh",
    "by": [{ "spec": "004-auth-v2", "construct": "RefreshRotating" }]
  }
]
```

The construct must have obligations, and each superseding rule must be a live rule of a later spec. Comments in the
spec are not read: this file is the only record of what is superseded.

`specs/plan/pending.json` (optional): known gaps. A pending obligation is reported (`pending: N` in the report line)
but does not fail the gate, so a stricter gate can be adopted before every gap is closed. An entry that is covered,
out of scope, superseded or not in the plan is a problem, so the list only shrinks. Pass it as the last argument of
`Coverage.reconcile` (decoded by `AlliumJson.pending`).

```json
[
  { "spec": "001-auth", "obligation": "surface-guarantee.Discovery.IssuerMatchesConfig", "reason": "no boundary test yet" }
]
```

### Settings

`Coverage.reconcile` takes `Settings`:

- `layout`: the paths in messages (spec files, test directories, data files);
- `specOrder`: which specs are "later" (default: by the number the spec name starts with, `002-x` after `001-y`);
- `tagPrefix`: default `@obligation:`.

## Allium CLI

`AlliumCli.download(Release.latest, cacheDir)` downloads the release for this machine (Linux and macOS, x86_64 and
arm64) and checks its SHA-256. `juxt/allium-tools` publishes no checksums, so `Release` pins them. For another version,
pass your own `Release(version, checksums)`. `AlliumCli.at(path)` uses a binary you already have.

The CLI exits non-zero on warnings and on error diagnostics, and still prints its JSON report. So only an empty stdout
is an error.

## Development

```
./sbt ci
```

The tests of `allium-cli`, `allium-circe` and `allium-lint` download the pinned CLI into `target/allium-cli`.

## Publishing

`sbt-ci-release` publishes to Maven Central from CI:

- each push to `main` publishes a snapshot;
- each published GitHub release (tag `vX.Y.Z`) publishes that version.

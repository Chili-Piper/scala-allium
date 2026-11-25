# Design

Origin: the Allium ↔ Gherkin traceability gate of `Chili-Piper/edge-fire-service`
(`project/Allium.scala`, `app/src/test/scala/com/chilipiper/edge/bdd/Allium*.scala`), extracted on 2026-11-20.

## Goals

- A library that other Scala projects can use for the "every `allium plan` obligation is covered by a test" gate.
- Small modules with few dependencies. Users take only what they need.
- Public (Apache-2.0, Maven Central).

## Modules

| Module            | Depends on                 | Contents                                                            |
|-------------------|----------------------------|---------------------------------------------------------------------|
| `allium-model`    | –                          | Case classes only: `Obligation`, `Plan`, `Outline`, data file entries |
| `allium-circe`    | model, circe-core/jawn     | Decoders for `allium plan/parse/check` JSON and the data files      |
| `allium-cli`      | – (JDK only)               | Download + SHA-256 check + run the CLI                              |
| `allium-coverage` | model                      | Pure reconciliation engine                                          |
| `allium-gherkin`  | coverage, io.cucumber:gherkin | Feature parsing, `@obligation:` tags, skip-tag policy           |
| `allium-scalatest` | coverage, scalatest       | `obligation:<id>` tags of ScalaTest suites                          |
| `allium-munit`    | coverage, munit            | `obligation:<id>` tags of munit suites                              |
| `allium-scalacheck` | scalacheck               | `InvariantProperty`: an invariant checked after every step          |
| `allium-lint`     | model, circe               | Undeclared `alias/config.param`; fatal warning codes                |
| `examples`        | all (not published)        | The README example, end to end, on a fixture repository             |

Scala 2.13 and 3.3 (LTS). No sbt plugin: the build-time generation of edge-fire (`Test/alliumGenerate`) is replaced by
calling `allium-cli` from the test itself.

## Decisions

| Item from edge-fire                                    | Decision                                                             |
|--------------------------------------------------------|----------------------------------------------------------------------|
| `@obligation:<id>` coverage, `out-of-scope.json`, `superseded.json` | Library (`allium-coverage`, `allium-gherkin`)              |
| `undeclaredConfigReferences` (CLI gap)                 | Library (`allium-lint`); reported upstream: https://github.com/juxt/allium-tools/issues/135 |
| `-- @superseded` markers read from source              | Dropped: `superseded.json` is the only record; no source scanning  |
| Section headings (`-- ── X ──` banners)                | Dropped: only the naming layer used them                             |
| Scenario/Feature naming layer (`rule X — …`, `specs/X.allium § S`) | Dropped from the library; to be removed from edge-fire too (`@obligation` tags cover it) |
| Skip-tag ban (`@wip`, `@ignore`, …)                    | Library, configurable (`GherkinCoverage.skippedScenarios`)           |
| Fatal warning codes                                    | Library, configurable (`AlliumLint.failing`)                         |
| "Later spec" = higher `NNN-` prefix                    | Library, configurable (`SpecOrder`)                                  |
| Paths in messages (`specs/…`, `features/…`)            | Library, configurable (`Layout`)                                     |
| `BddSuites` check (every feature dir has a runner)     | Stays in edge-fire (edge's own runner)                               |

## Not done yet

- Migrate edge-fire to the library (separate PR, on the first release).
- A one-call wrapper of the README example per test framework.

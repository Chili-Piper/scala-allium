# Testing strategy: which test fits which Allium construct

scala-allium checks one thing: every obligation of a spec has a test. It does not require one kind of test. This page
says which kind of test fits each Allium construct best, which test frameworks can report coverage, and how the
constructs that `allium plan` does not cover yet are added.

## Terms

| Term | What it is | Written by | Formal? | `allium plan` obligation? |
|---|---|---|---|---|
| Obligation | One test that must exist, with a stable id (`rule-failure.RefreshAccessToken.3`) | `allium plan`, derived from the spec | Generated | It is the output of `allium plan` |
| Top-level `invariant` | A statement over the domain state that must hold after every change | Spec author | Yes, a checked expression | Yes: `invariant.<Name>` |
| Contract `@invariant` | A property that every implementation of a `contract` must have | Spec author | No, prose | No (see [Constructs without a plan obligation](#constructs-without-a-plan-obligation)) |
| Surface `@guarantee` | A promise that a `surface` (an API boundary) makes to its callers | Spec author | No, prose | No (same) |

## The best test for each construct

"BDD" means a Gherkin scenario that runs the real code (with a real database where the rule touches persistence).
"Property" means a ScalaCheck property: generated inputs, a law that must hold for all of them.

| Construct / plan category | Example | Best test | Also good | Why |
|---|---|---|---|---|
| `rule_success`, `rule_failure` (one per `requires` clause), `rule_entity_creation` | `RefreshAccessToken` rejects a revoked token | BDD | Unit test of the rule's function | One concrete case per clause, readable by people, one tag per obligation id |
| `transition_edge`, `transition_rejected`, `transition_terminal` | `AuthCode` goes `issued → used`, never back | BDD for each edge | Stateful property (ScalaCheck `Commands`) | Scenarios show each allowed or rejected edge; a stateful property finds illegal paths nobody wrote down |
| `when_set`, `when_clear`, `when_presence` | A field is set exactly in one state | BDD or unit | Stateful property | Same as transitions |
| `invariant` (top-level) | `OrdinaryUserSessionsManageNothing` | Stateful property in memory: random sequences of operations on the pure rules, the invariant checked after each step | One BDD example when the store enforces it (a unique index) | The invariant says "after every change". One scenario checks one path; a generator checks thousands, but only when each case costs microseconds |
| `temporal` | An auth code expires after N minutes | Unit test with a controlled clock (cats-effect `TestControl`) | BDD with a clock step | No real waiting; deterministic |
| `derived`, `projection` | A computed field | Unit or property test of the function | – | Pure computation; a scenario adds only ceremony |
| `config_default` | Default token lifetime | Unit test of the config loader | – | One fact |
| `surface_provides`, `surface_actor`, `surface_exposure` | An endpoint exists; only the allowed actor may call it | HTTP/MCP integration test or BDD at the boundary | – | Boundary behaviour; test it through the real boundary |
| `entity_fields`, `entity_optional`, `entity_relationship`, `sum_type_variant` | The spec's `AccessTokenClaims.aud` exists in the code | Codec golden test or a structural test (spec shape vs. the code's type) | The compiler, where the type is the proof | Shape, not behaviour. A scenario that says "the field exists" exists only to satisfy the gate |
| `value_equality`, `enum_comparable` | Two equal claims are `==` | Property test of the laws (reflexive, symmetric, `Order` consistent with `==`) | Out of scope when the type is a case class (equality by construction) | A law over all values, not one example |
| `contract_signature` | `sign_access_jwt` exists with this signature | Unit test that calls it | The compiler | Mostly a compile-time fact; one call proves it is wired |
| Contract `@invariant` | `HashDefinition`: SHA-256, lowercase hex | Unit tests with known answers (published test vectors) plus properties (deterministic, format, distinct inputs → distinct outputs) | – | A property of a function no user sees. Some are not testable (collision resistance, randomness quality): enforce them by construction (`SecureRandom`) and review |
| Surface `@guarantee` | `DiscoveryIssuerMatchesConfig` | Integration test at the boundary (HTTP/MCP) | BDD when it is one concrete promise | A promise at the API edge. A guarantee about *every* endpoint needs a test that iterates all endpoints, not one scenario |

Rules of thumb:

- Behaviour → BDD. Laws over all values or all sequences → properties. Facts about one function → unit tests.
  Shape → types, golden tests, structural checks.
- Prefer turning prose into a formal construct (a top-level `invariant`, a `requires` clause, a `derived` value). Then
  `allium plan` gives it an obligation and the gate sees it.
- A fast test may cover the same obligation as a slow one. Avoid two slow tests for one obligation.
- Property tests run in memory, like unit tests. A property earns its value from many cases (hundreds of inputs or
  operation sequences in milliseconds); against a real database each case is a round trip, so the run gets few cases
  and is still slow. Test the pure rules (the function that decides, the codec that stores) with properties.
- An invariant that only the store enforces (a unique index, an SQL query) is not a property of the code: an in-memory
  model of it would pass with the index dropped. Give it one slow test (a BDD example or a database test), not a
  property against the database next to a scenario.

## Test frameworks that can report coverage

The gate reconciles obligation ids with *tags*. Any test that carries the tag `obligation:<id>` counts. Each source
reads tags from the test framework's own model of the tests; scala-allium never scans source files.

| Module | Status | Source of tags | Use it for |
|---|---|---|---|
| `allium-gherkin` | Exists | `@obligation:<id>` on a scenario, its `Feature:`/`Rule:` or `Examples:` | BDD scenarios |
| `allium-scalatest` | Exists | `Tag("obligation:<id>")` on a test (`Suite.tags`, nested suites included) of the suites you pass | ScalaTest unit, integration and property tests |
| `allium-munit` | Exists | `Tag("obligation:<id>")` on a test (`Test.tags`) | munit unit, integration and property tests |
| `allium-scalacheck` | Exists | – | `InvariantProperty`: random operation sequences, each operation generated from the current state, the invariant checked after every step. Not a tag source: ScalaCheck properties run inside ScalaTest or munit and are tagged there |

All sources produce the same `Coverage` input, so one project can mix them: scenarios for rules, properties for
invariants, unit tests for contracts.

## Constructs without a plan obligation

`allium plan` (3.6.1, 3.7.2) emits nothing for surface `@guarantee` and contract `@invariant`. `allium parse` does
return them: a `Surface` or `Contract` block whose items include an `Annotation` of `kind` `Guarantee` or `Invariant`
with a `name`. scala-allium derives obligations from that output, with these ids:

| Construct | Id | Category |
|---|---|---|
| `@guarantee G` in `surface S` | `surface-guarantee.S.G` | `surface_guarantee` |
| `@invariant I` in `contract C` | `contract-invariant.C.I` | `contract_invariant` |

The same ids and categories are proposed upstream for `allium plan`
([juxt/allium-tools](https://github.com/juxt/allium-tools)). When a CLI release emits them, scala-allium reads them from
`allium plan` and stops deriving them; tags stay the same.

`@guidance` annotations have no name and are not obligations.

## Adopting a stricter gate: the baseline

A new kind of obligation (for example guarantees) can make many obligations missing at once. A baseline file,
`pending.json`, lists them:

```json
[
  { "spec": "001-mcp-oauth-oidc", "obligation": "surface-guarantee.OAuthDiscovery.DiscoveryIssuerMatchesConfig", "reason": "no boundary test yet" }
]
```

- A missing obligation in the baseline is reported, but does not fail the gate.
- A missing obligation not in the baseline fails the gate.
- A baseline entry that is covered, out of scope or not in the plan fails the gate (stale entry), so the list only
  shrinks.

Roll out in small steps: add the new check with a baseline of today's gaps, then remove entries as tests arrive.

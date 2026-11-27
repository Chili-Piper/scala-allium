# Testing strategy: which test fits which Allium construct

scala-allium checks one thing: every obligation of a spec has a test. It does not require one kind of test. This page
recommends Gherkin scenarios for all of them, says which technique the step of each Allium construct should use, which
test frameworks can report coverage, and how the constructs that `allium plan` does not cover yet are added.

## Terms

| Term | What it is | Written by | Formal? | `allium plan` obligation? |
|---|---|---|---|---|
| Obligation | One test that must exist, with a stable id (`rule-failure.RefreshAccessToken.3`) | `allium plan`, derived from the spec | Generated | It is the output of `allium plan` |
| Top-level `invariant` | A statement over the domain state that must hold after every change | Spec author | Yes, a checked expression | Yes: `invariant.<Name>` |
| Contract `@invariant` | A property that every implementation of a `contract` must have | Spec author | No, prose | No (see [Constructs without a plan obligation](#constructs-without-a-plan-obligation)) |
| Surface `@guarantee` | A promise that a `surface` (an API boundary) makes to its callers | Spec author | No, prose | No (same) |

## Prefer Gherkin scenarios

Write every test that covers an obligation as a Gherkin scenario (Cucumber), whatever technique checks it. Reasons:

- The scenario is English, close to the spec it tests: a reviewer reads the claim next to the rule, invariant or
  guarantee it covers.
- One coverage source (`allium-gherkin`): one tag syntax, one place to look for what is tested.
- The technique is the step definition's business. A step can check one example, run a ScalaCheck property on
  hundreds of generated inputs, drive a controlled clock, or compare against known answers.

The ScalaTest and munit sources (below) are for projects that do not use Cucumber, or for a test that cannot be a
scenario.

"Example" below means a scenario with fixed data on the real code (with a real database where the rule touches
persistence). "For any" means a scenario whose step runs a ScalaCheck property in memory (see
[Properties in a step](#properties-in-a-step)).

| Construct / plan category | Scenario form | Technique in the step | Why |
|---|---|---|---|
| `rule_success`, `rule_failure` (one per `requires` clause), `rule_entity_creation` | Example | The real rule | One concrete case per clause |
| `transition_edge`, `transition_rejected`, `transition_terminal`, `when_set`, `when_clear`, `when_presence` | Example per edge; optionally "for any sequence" | The real rule; `InvariantProperty` over the pure transition function | Each edge stays visible; a sequence finds illegal paths nobody wrote down |
| `invariant` (top-level), kept by the code | "For any" (inputs, or sequences of rules) | `Prop.forAll` / `InvariantProperty` on the pure rules, in memory | The invariant says "after every change": a generator checks thousands of cases |
| `invariant` (top-level), kept only by the store (a unique index) | Example | The real store | An in-memory model passes with the index dropped |
| `temporal` | Example with a clock step ("When 10 minutes pass") | cats-effect `TestControl` or an injected clock | No real waiting; deterministic |
| `derived`, `projection`, `config_default` | Example, or "for any" | The pure function / the config loader | One fact, or a law over all inputs |
| `surface_provides`, `surface_actor`, `surface_exposure`, surface `@guarantee` | Example at the boundary; "for every endpoint" when the guarantee is about all of them | HTTP/MCP calls to the real routes | A promise at the API edge |
| Contract `@invariant` | `Scenario Outline` with the known answers as `Examples:`, plus "for any" for the format laws | The function; published test vectors | The vectors are the table; the laws (deterministic, lowercase hex) hold for all inputs. Some are not testable (randomness quality): enforce them by construction and review |
| `value_equality`, `enum_comparable` | "For any" ("equality of claims is lawful") | discipline laws (cats-kernel-laws) run in the step | A law over all values. Out of scope when equality comes by construction (a case class) |
| `entity_fields`, `entity_optional`, `entity_relationship`, `sum_type_variant`, `contract_signature` | None: no scenario | The compiler; a codec golden test | Shape, not behaviour. A scenario that says "the field exists" exists only to satisfy the gate: mark these out of scope |

Rules of thumb:

- Prefer turning prose into a formal construct (a top-level `invariant`, a `requires` clause, a `derived` value). Then
  `allium plan` gives it an obligation and the gate sees it.
- A fast test may cover the same obligation as a slow one. Avoid two slow tests for one obligation.
- Property tests run in memory, like unit tests. A property earns its value from many cases (hundreds of inputs or
  operation sequences in milliseconds); against a real database each case is a round trip, so the run gets few cases
  and is still slow. Test the pure rules (the function that decides, the codec that stores) with properties.
- An invariant that only the store enforces (a unique index, an SQL query) is not a property of the code: an in-memory
  model of it would pass with the index dropped. Give it one slow test (an example scenario), not a property against
  the database.

### Scenario shapes

An example (a rule):

```gherkin
@obligation:rule-failure.RefreshAccessToken.3
Scenario: rule RefreshAccessToken — a revoked refresh token is rejected
  Given a client with a revoked refresh token
  When the client refreshes its access token
  Then the token endpoint answers "invalid_grant"
```

"For any" (a top-level invariant kept by the code):

```gherkin
@obligation:invariant.WorkspaceManagerGrantsHaveWorkspaces
Scenario: invariant WorkspaceManagerGrantsHaveWorkspaces — holds for the session of any roles, tenant and gates
  Given any roles, tenant and gate configuration
  When the session is derived and stored
  Then it is a Super Admin's, an ordinary user's, or manages at least one workspace
```

"For any sequence" (an invariant that depends on the order of rules):

```gherkin
@obligation:invariant.NonNegativeStock
Scenario: invariant NonNegativeStock — holds after any sequence of deliveries and orders
  Given any sequence of deliveries and orders, starting from an empty stock
  Then the stock is never negative, from the start and after every step
```

Known answers (a contract invariant):

```gherkin
@obligation:contract-invariant.CryptoOps.HashDefinition
Scenario Outline: contract CryptoOps, invariant HashDefinition — SHA-256 as lowercase hex
  When "<input>" is hashed
  Then the hash is "<hex>"

  Examples:
    | input | hex                                                              |
    | abc   | ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad |
```

A clock step (a temporal rule):

```gherkin
@obligation:temporal.ExpireAuthCode
Scenario: rule ExpireAuthCode — an auth code expires after its lifetime
  Given an auth code issued now
  When 10 minutes pass
  Then redeeming the auth code fails with "invalid_grant"
```

### Properties in a step

A "for any" step builds the property and runs it with `allium-scalacheck` `PropertyStep.check`, which returns the
verdict as a value (`Left` with the counterexample and the labels):

```scala
Then("""it is a Super Admin's, an ordinary user's, or manages at least one workspace""") { () =>
  val prop = Prop.forAll(rolesGen, tenantGen, gatesGen) { (roles, tenant, gates) =>
    derive(gates, tenant, roles).forall(s => s.isSuperAdmin || s.selfOnly || s.managedWorkspaces.nonEmpty)
  }
  PropertyStep.check("WorkspaceManagerGrantsHaveWorkspaces", prop).left.foreach(fail(_))
}
```

The generators and the pure function live in Scala; the scenario states the claim. Put the generators in the glue
class of the spec, next to the other steps. `InvariantProperty` gives the "for any sequence" form.

## Test frameworks that can report coverage

The gate reconciles obligation ids with *tags*. Any test that carries the tag `obligation:<id>` counts. Each source
reads tags from the test framework's own model of the tests; scala-allium never scans source files.

| Module | Status | Source of tags | Use it for |
|---|---|---|---|
| `allium-gherkin` | Exists | `@obligation:<id>` on a scenario, its `Feature:`/`Rule:` or `Examples:` | BDD scenarios |
| `allium-scalatest` | Exists | `Tag("obligation:<id>")` on a test (`Suite.tags`, nested suites included) of the suites you pass | ScalaTest unit, integration and property tests |
| `allium-munit` | Exists | `Tag("obligation:<id>")` on a test (`Test.tags`) | munit unit, integration and property tests |
| `allium-scalacheck` | Exists | – | `InvariantProperty`: random operation sequences, each operation generated from the current state, the invariant checked after every step. `PropertyStep.check`: run a property inside a Cucumber step (or any test) and get the verdict as a value. Not a tag source: properties run inside a scenario, a ScalaTest or a munit test and are tagged there |

All sources produce the same `Coverage` input, so one project can mix them. Prefer one: Gherkin.

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

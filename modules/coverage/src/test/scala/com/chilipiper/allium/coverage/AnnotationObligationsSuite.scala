package com.chilipiper.allium.coverage

import com.chilipiper.allium.model._

class AnnotationObligationsSuite extends munit.FunSuite {

  private val span = Span(0, 1)

  private def annotation(kind: AnnotationKind, name: Option[String], body: String*): Annotation =
    Annotation(kind, name, body.toList, span)

  private val outline = Outline(
    List(
      Declaration(
        DeclarationKind.Surface,
        Some("OAuthDiscovery"),
        span,
        List(
          annotation(AnnotationKind.Guarantee, Some("IssuerMatchesConfig"), "Responses use", "config.issuer."),
          annotation(AnnotationKind.Guarantee, Some("ReadOnly")),
          annotation(AnnotationKind.Other("Guidance"), None, "GET /.well-known/…"),
        ),
      ),
      Declaration(
        DeclarationKind.Contract,
        Some("CryptoOps"),
        span,
        List(annotation(AnnotationKind.Invariant, Some("HashDefinition"), "SHA-256, lowercase hex.")),
      ),
      // An @invariant outside a contract and a @guarantee outside a surface are not obligations.
      Declaration(DeclarationKind.Entity, Some("Client"), span, List(annotation(AnnotationKind.Invariant, Some("X")))),
      Declaration(DeclarationKind.Rule, Some("R"), span, List(annotation(AnnotationKind.Guarantee, Some("Y")))),
    ),
    Nil,
  )

  test("a guarantee of a surface and an invariant of a contract are obligations, with the upstream ids") {
    assertEquals(
      AnnotationObligations.of(outline).map(o => (o.id.value, o.category, o.construct)),
      List(
        (
          "surface-guarantee.OAuthDiscovery.IssuerMatchesConfig",
          "surface_guarantee",
          "OAuthDiscovery.IssuerMatchesConfig",
        ),
        ("surface-guarantee.OAuthDiscovery.ReadOnly", "surface_guarantee", "OAuthDiscovery.ReadOnly"),
        ("contract-invariant.CryptoOps.HashDefinition", "contract_invariant", "CryptoOps.HashDefinition"),
      ),
    )
  }

  test("the body of the annotation is the expression; an annotation without a body has none") {
    assertEquals(
      AnnotationObligations.of(outline).map(_.expression),
      List(Some("Responses use config.issuer."), None, Some("SHA-256, lowercase hex.")),
    )
  }

  test("add keeps the obligations the plan already has, so a CLI that emits them changes nothing") {
    val fromCli = Obligation(
      ObligationId("surface-guarantee.OAuthDiscovery.ReadOnly"),
      "surface_guarantee",
      "OAuthDiscovery.ReadOnly",
      "from the CLI",
      None,
    )
    val plan = AnnotationObligations.add(Plan(List(fromCli), Nil), outline)
    assertEquals(plan.obligations.map(_.id.value).sorted, AnnotationObligations.of(outline).map(_.id.value).sorted)
    assertEquals(plan.obligations.find(_.id == fromCli.id).map(_.description), Some("from the CLI"))
  }

  test("a missing guarantee is missing; a tagged one is covered") {
    val plan = SpecPlan(SpecName("001-a"), AnnotationObligations.add(Plan(Nil, Nil), outline))
    val tag = Tag(SpecName("001-a"), ObligationId("surface-guarantee.OAuthDiscovery.ReadOnly"), "a.feature:3")
    val result = Coverage.reconcile(List(plan), List(tag), Nil, Nil)
    assertEquals(result.reports.map(r => (r.covered, r.missing)), List((1, 2)))
  }
}

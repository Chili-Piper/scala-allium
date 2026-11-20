package com.chilipiper.allium.circe

import com.chilipiper.allium.model._
import io.circe.{Decoder, HCursor, Json}

/** Decoders for the JSON that the `allium` CLI prints. They read only the fields this library models and ignore the
  * rest, so a newer CLI that adds fields still decodes.
  */
object AlliumCodecs {

  implicit val spanDecoder: Decoder[Span] = Decoder.forProduct2("start", "end")(Span.apply)

  implicit val severityDecoder: Decoder[Severity] = Decoder.decodeString.map(Severity.parse)

  implicit val locationDecoder: Decoder[Location] =
    (c: HCursor) =>
      for {
        file <- c.get[Option[String]]("file")
        line <- c.get[Option[Int]]("line")
        column <- c.get[Option[Int]]("col")
      } yield Location(file, line, column)

  implicit val diagnosticDecoder: Decoder[Diagnostic] =
    (c: HCursor) =>
      for {
        code <- c.get[Option[String]]("code")
        severity <- c.get[Severity]("severity")
        message <- c.get[String]("message")
        location <- c.get[Option[Location]]("location")
        span <- c.get[Option[Span]]("span")
      } yield Diagnostic(code, severity, message, location, span)

  implicit val obligationIdDecoder: Decoder[ObligationId] = Decoder.decodeString.map(ObligationId(_))

  /** A `source_span` with a missing `start` or `end` is no span. */
  private val optionalSpan: Decoder[Option[Span]] =
    (c: HCursor) =>
      for {
        start <- c.get[Option[Int]]("start")
        end <- c.get[Option[Int]]("end")
      } yield start.zip(end).map { case (s, e) => Span(s, e) }

  implicit val obligationDecoder: Decoder[Obligation] =
    (c: HCursor) =>
      for {
        id <- c.get[ObligationId]("id")
        category <- c.get[String]("category")
        construct <- c.get[String]("source_construct")
        description <- c.get[String]("description")
        span <- c.downField("source_span").success.fold[Decoder.Result[Option[Span]]](Right(None))(optionalSpan(_))
      } yield Obligation(id, category, construct, description, span)

  private def diagnostics(c: HCursor): Decoder.Result[List[Diagnostic]] =
    c.get[Option[List[Diagnostic]]]("diagnostics").map(_.getOrElse(Nil))

  /** `allium plan <spec>`. */
  implicit val planDecoder: Decoder[Plan] =
    (c: HCursor) =>
      for {
        obligations <- c.get[List[Obligation]]("obligations")
        ds <- diagnostics(c)
      } yield Plan(obligations, ds)

  /** One object of `allium check <dir>`: it prints one per spec file. */
  implicit val checkReportDecoder: Decoder[CheckReport] =
    (c: HCursor) =>
      for {
        file <- c.get[Option[String]]("spec_file")
        ds <- diagnostics(c)
      } yield CheckReport(file, ds)

  private val declarationKind: String => DeclarationKind = {
    case "Rule"     => DeclarationKind.Rule
    case "Surface"  => DeclarationKind.Surface
    case "Contract" => DeclarationKind.Contract
    case "Entity"   => DeclarationKind.Entity
    case "Config"   => DeclarationKind.Config
    case other      => DeclarationKind.Other(other)
  }

  private val annotationKind: String => AnnotationKind = {
    case "Invariant" => AnnotationKind.Invariant
    case "Guarantee" => AnnotationKind.Guarantee
    case other       => AnnotationKind.Other(other)
  }

  /** A `{"name": …}` object; absent or `null` (a `config` block has no name) is no name. */
  private def name(c: io.circe.ACursor): Decoder.Result[Option[String]] =
    c.success.filterNot(_.value.isNull).fold[Decoder.Result[Option[String]]](Right(None))(_.get[Option[String]]("name"))

  private val annotationDecoder: Decoder[Option[Annotation]] =
    (c: HCursor) => {
      val a = c.downField("kind").downField("Annotation")
      a.success.fold[Decoder.Result[Option[Annotation]]](Right(None)) { a =>
        for {
          kind <- a.get[String]("kind")
          n <- name(a.downField("name"))
          body <- a.get[Option[List[String]]]("body")
          span <- a.get[Span]("span")
        } yield Some(Annotation(annotationKind(kind), n, body.getOrElse(Nil), span))
      }
    }

  /** One element of `module.declarations`: `{"Block": …}`, `{"Invariant": …}`, `{"Use": …}`, … */
  private val declarationDecoder: Decoder[Declaration] =
    (c: HCursor) =>
      c.keys.flatMap(_.headOption) match {
        case Some("Block") =>
          val b = c.downField("Block")
          for {
            kind <- b.get[String]("kind")
            n <- name(b.downField("name"))
            span <- b.get[Span]("span")
            items <- b.get[Option[List[Json]]]("items")
            annotations <- items.getOrElse(Nil).foldRight[Decoder.Result[List[Annotation]]](Right(Nil)) { (j, acc) =>
              for {
                a <- annotationDecoder.decodeJson(j)
                rest <- acc
              } yield a.toList ++ rest
            }
          } yield Declaration(declarationKind(kind), n, span, annotations)
        case Some(key) =>
          val d = c.downField(key)
          val kind = key match {
            case "Invariant" => DeclarationKind.Invariant
            case "Use"       => DeclarationKind.Use
            case other       => DeclarationKind.Other(other)
          }
          for {
            n <- name(d.downField(if (key == "Use") "alias" else "name"))
            span <- d.get[Span]("span")
          } yield Declaration(kind, n, span, Nil)
        case None => Left(io.circe.DecodingFailure("expected a declaration object", c.history))
      }

  /** `allium parse <spec>`. */
  implicit val outlineDecoder: Decoder[Outline] =
    (c: HCursor) =>
      for {
        declarations <- c
          .downField("module")
          .get[Option[List[Declaration]]]("declarations")(Decoder.decodeOption(Decoder.decodeList(declarationDecoder)))
          .map(_.getOrElse(Nil))
        ds <- diagnostics(c)
      } yield Outline(declarations, ds)

}

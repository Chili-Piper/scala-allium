package com.chilipiper.allium.circe

import com.chilipiper.allium.model.{ConstructRef, ObligationId, OutOfScope, Pending, Selector, SpecName, Supersession}
import io.circe.{ACursor, Decoder, DecodingFailure, HCursor}

/** Decoders for the data files of the coverage gate. People write these files by hand, so an unknown field is an error:
  * a misspelled `categroy` must not turn an entry into a different one.
  */
object DataFileCodecs {

  private def only(c: HCursor, allowed: Set[String]): Decoder.Result[Unit] =
    c.keys.map(_.filterNot(allowed).toList).getOrElse(Nil) match {
      case Nil     => Right(())
      case unknown =>
        Left(
          DecodingFailure(s"unknown fields ${unknown.mkString(", ")}; expected ${allowed.mkString(", ")}", c.history),
        )
    }

  private def spec(c: ACursor): Decoder.Result[SpecName] = c.as[String].map(SpecName(_))

  /** `{"spec": "001-a", "obligation": "value-equality.V", "reason": "…"}`, or `{"spec"?: …, "category": "<kind>",
    * "reason": "…"}`. Without `spec`, a category applies to every spec; an obligation always needs a spec.
    */
  implicit val outOfScopeDecoder: Decoder[OutOfScope] =
    (c: HCursor) =>
      for {
        _ <- only(c, Set("spec", "obligation", "category", "reason"))
        s <- c.get[Option[String]]("spec").map(_.map(SpecName(_)))
        obligation <- c.get[Option[String]]("obligation")
        category <- c.get[Option[String]]("category")
        reason <- c.get[String]("reason")
        selector <- (s, obligation, category) match {
          case (Some(_), Some(id), None) => Right(Selector.Id(ObligationId(id)))
          case (None, Some(_), None)     => Left(DecodingFailure("an obligation needs a spec", c.history))
          case (_, None, Some(kind))     => Right(Selector.Kind(kind))
          case _ => Left(DecodingFailure("expected exactly one of obligation, category", c.history))
        }
      } yield OutOfScope(s, selector, reason)

  private val constructRefDecoder: Decoder[ConstructRef] =
    (c: HCursor) =>
      for {
        _ <- only(c, Set("spec", "construct"))
        s <- spec(c.downField("spec"))
        construct <- c.get[String]("construct")
      } yield ConstructRef(s, construct)

  /** `{"spec": "001-a", "construct": "Old", "by": [{"spec": "002-b", "construct": "New"}]}`; `by` is not empty. */
  implicit val supersessionDecoder: Decoder[Supersession] =
    (c: HCursor) =>
      for {
        _ <- only(c, Set("spec", "construct", "by"))
        s <- spec(c.downField("spec"))
        construct <- c.get[String]("construct")
        by <- c.get[List[ConstructRef]]("by")(Decoder.decodeList(constructRefDecoder))
        _ <- if (by.isEmpty) Left(DecodingFailure("`by` names no superseding rule", c.history)) else Right(())
      } yield Supersession(ConstructRef(s, construct), by)

  /** `{"spec": "001-a", "obligation": "surface-guarantee.S.G", "reason": "…"}`: a known gap. */
  implicit val pendingDecoder: Decoder[Pending] =
    (c: HCursor) =>
      for {
        _ <- only(c, Set("spec", "obligation", "reason"))
        s <- spec(c.downField("spec"))
        obligation <- c.get[String]("obligation")
        reason <- c.get[String]("reason")
      } yield Pending(s, ObligationId(obligation), reason)

}

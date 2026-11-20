package com.chilipiper.allium.circe

import com.chilipiper.allium.model.{CheckReport, OutOfScope, Outline, Plan, Supersession}
import io.circe.jawn.CirceSupportParser
import io.circe.{Decoder, Json}
import org.typelevel.jawn.AsyncParser

import AlliumCodecs._

/** Reads the stdout of the `allium` CLI. Errors are messages, for test reports. */
object AlliumJson {

  /** Every JSON value of `text`. `allium check` prints one object per spec file; the other commands print one. */
  def values(text: String): Either[String, List[Json]] = {
    val parser = CirceSupportParser.async(AsyncParser.ValueStream)
    (for {
      a <- parser.absorb(text)(CirceSupportParser.facade)
      b <- parser.finish()(CirceSupportParser.facade)
    } yield (a ++ b).toList).left.map(_.getMessage)
  }

  /** The single JSON value of `text`. */
  def single(text: String): Either[String, Json] =
    values(text).flatMap {
      case one :: Nil => Right(one)
      case other      => Left(s"expected one JSON value, got ${other.size}")
    }

  private def decode[A: Decoder](json: Json): Either[String, A] = json.as[A].left.map(_.getMessage)

  /** The stdout of `allium plan <spec>`. */
  def plan(text: String): Either[String, Plan] = single(text).flatMap(decode[Plan])

  /** The stdout of `allium parse <spec>`. */
  def outline(text: String): Either[String, Outline] = single(text).flatMap(decode[Outline])

  /** An out-of-scope file: a JSON array of entries (see [[DataFileCodecs]]). */
  def outOfScope(text: String): Either[String, List[OutOfScope]] =
    single(text).flatMap(decode[List[OutOfScope]](_)(Decoder.decodeList(DataFileCodecs.outOfScopeDecoder)))

  /** A superseded file: a JSON array of entries (see [[DataFileCodecs]]). */
  def superseded(text: String): Either[String, List[Supersession]] =
    single(text).flatMap(decode[List[Supersession]](_)(Decoder.decodeList(DataFileCodecs.supersessionDecoder)))

  /** The stdout of `allium check <dir>`. */
  def check(text: String): Either[String, List[CheckReport]] =
    values(text).flatMap(vs =>
      vs.foldRight[Either[String, List[CheckReport]]](Right(Nil)) { (j, acc) =>
        for {
          r <- decode[CheckReport](j)
          rest <- acc
        } yield r :: rest
      },
    )

}

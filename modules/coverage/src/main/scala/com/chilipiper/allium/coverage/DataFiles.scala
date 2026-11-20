package com.chilipiper.allium.coverage

import com.chilipiper.allium.model.{ObligationId, SpecName}

/** The two data files of the coverage gate. `#` starts a comment; blank lines are ignored. */
object DataFiles {

  /** Lines without comments and blank lines, with their 1-based line numbers. */
  private def entries(lines: List[String]): List[(String, Int)] =
    lines.zipWithIndex.map { case (l, i) => l.takeWhile(_ != '#').trim -> (i + 1) }.filter(_._1.nonEmpty)

  private def split[A](parsed: List[Either[String, A]]): (List[A], List[String]) =
    (parsed.collect { case Right(e) => e }, parsed.collect { case Left(p) => p })

  /** `<spec|*> <obligation id | category:<kind>> <reason…>` per line. `*` (every spec) only with a category. */
  def parseOutOfScope(file: String, lines: List[String]): (List[OutOfScope], List[String]) =
    split(entries(lines).map { case (l, n) =>
      l.split("\\s+", 3).toList match {
        case spec :: selector :: reason :: Nil =>
          val sel =
            if (selector.startsWith("category:"))
              Selector.Kind(selector.stripPrefix("category:"))
            else
              Selector.Id(ObligationId(selector))
          (spec, sel) match {
            case ("*", Selector.Id(_)) => Left(s"$file:$n: an obligation id needs a spec, not `*`")
            case ("*", _)              => Right(OutOfScope(None, sel, reason, n))
            case _                     => Right(OutOfScope(Some(SpecName(spec)), sel, reason, n))
          }
        case _ => Left(s"$file:$n: expected `<spec|*> <obligation id|category:<kind>> <reason>`, got: $l")
      }
    })

  /** `<spec>/<Construct> <spec>/<Construct>…` per line: the construct, then what supersedes it. */
  def parseSuperseded(file: String, lines: List[String]): (List[Supersession], List[String]) = {
    def ref(s: String): Option[ConstructRef] =
      s.split('/') match {
        case Array(spec, construct) if spec.nonEmpty && construct.nonEmpty =>
          Some(ConstructRef(SpecName(spec), construct))
        case _ => None
      }
    split(entries(lines).map { case (l, n) =>
      l.split("\\s+").toList.map(ref) match {
        case Some(construct) :: by if by.nonEmpty && by.forall(_.isDefined) =>
          Right(Supersession(construct, by.flatten, n))
        case _ => Left(s"$file:$n: expected `<spec>/<Construct> <spec>/<Construct>…`, got: $l")
      }
    })
  }

}

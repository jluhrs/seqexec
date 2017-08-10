package edu.gemini.seqexec.web.client.semanticui

import scalaz.Equal

sealed trait Size

object Size {
  case object NotSized extends Size
  case object Tiny extends Size
  case object Mini extends Size
  case object Medium extends Size
  case object Small extends Size
  case object Large extends Size
  case object Big extends Size
  case object Huge extends Size
  case object Massive extends Size

  implicit val equal: Equal[Size] = Equal.equalA[Size]
}

trait SemanticUISize extends Size

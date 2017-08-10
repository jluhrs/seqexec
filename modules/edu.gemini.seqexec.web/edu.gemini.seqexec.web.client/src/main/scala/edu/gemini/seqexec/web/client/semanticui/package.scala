package edu.gemini.seqexec.web.client

import japgolly.scalajs.react.vdom.html_<^.VdomAttr

package object semanticui extends SemanticUISize with SemanticUIWidth with SemanticUIAlign {
  // Custom attributes used by SemanticUI
  val dataTab     = VdomAttr("data-tab")
  val dataTooltip = VdomAttr("data-tooltip")
  val formId      = VdomAttr("form")

}

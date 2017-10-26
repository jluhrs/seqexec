// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.web.client.semanticui.elements.label

import edu.gemini.seqexec.web.client.semanticui.Size
import edu.gemini.seqexec.web.client.semanticui.elements.icon.Icon
import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

import scalaz.Equal
import scalaz.syntax.equal._

sealed trait Pointing

object Pointing {
  case object None extends Pointing
  case object Up extends Pointing
  case object Down extends Pointing
  case object Left extends Pointing
  case object Right extends Pointing

  implicit val equal: Equal[Pointing] = Equal.equalA[Pointing]
}

object Label {
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  final case class Props(text: String,
    htmlFor : Option[String] = None,
    color   : Option[String] = None,
    tag     : Boolean = false,
    basic   : Boolean = false,
    size    : Size = Size.NotSized,
    pointing: Pointing = Pointing.None,
    icon    : Option[Icon] = None)

  private val component = ScalaComponent.builder[Props]("Label")
    .stateless
    .renderPC((_, p, c) =>
      <.label(
        ^.cls := "ui label",
        ^.classSet(
          "basic"          -> p.basic,
          "tag"            -> p.tag,
          "tiny"           -> (p.size === Size.Tiny),
          "mini"           -> (p.size === Size.Mini),
          "small"          -> (p.size === Size.Small),
          "large"          -> (p.size === Size.Large),
          "big"            -> (p.size === Size.Big),
          "huge"           -> (p.size === Size.Huge),
          "massive"        -> (p.size === Size.Massive),
          "pointing"       -> (p.pointing === Pointing.Up),
          "pointing below" -> (p.pointing === Pointing.Down),
          "left pointing"  -> (p.pointing === Pointing.Left),
          "right pointing" -> (p.pointing === Pointing.Right)
        ),
        p.color.map(u => ^.cls := u).whenDefined,
        ^.htmlFor :=? p.htmlFor,
        p.icon.whenDefined,
        p.text,
        c
      )
    ).build

  def apply(p: Props, children: VdomNode*): Unmounted[Props, Unit, Unit] = component(p)(children: _*)
}

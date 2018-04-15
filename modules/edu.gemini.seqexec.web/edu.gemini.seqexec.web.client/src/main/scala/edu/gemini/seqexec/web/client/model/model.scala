// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.web.client

import diode.RootModelR
import diode.data.{Empty, Pot, RefTo}
import edu.gemini.seqexec.model.UserDetails
import edu.gemini.seqexec.model.Model._
import edu.gemini.seqexec.model.events.SeqexecEvent.ServerLogMessage
import edu.gemini.web.common.{Zipper, FixedLengthBuffer}
import org.scalajs.dom.WebSocket
import cats._
import cats.implicits._

@SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
object model {
  implicit val eqWebSocket: Eq[WebSocket] =
    Eq[(String, String, Int)].contramap { x =>
      (x.url, x.protocol, x.readyState)
    }

  implicit def eqRefTo[A: Eq]: Eq[RefTo[A]] =
    Eq.by(_.apply())


  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit def eqPot[A: Eq]: Eq[Pot[A]] = Eq.instance { (a, b) =>
    if (a.nonEmpty && b.nonEmpty)
      a.get  === b.get
    else (a == b)
  }

  // Pages
  object Pages {
    sealed trait SeqexecPages extends Product with Serializable

    case object Root extends SeqexecPages
    case object SoundTest extends SeqexecPages
    final case class InstrumentPage(instrument: Instrument) extends SeqexecPages
    final case class SequencePage(instrument: Instrument, obsId: SequenceId, step: StepId) extends SeqexecPages
    final case class SequenceConfigPage(instrument: Instrument, obsId: SequenceId, step: Int) extends SeqexecPages

    implicit val equal: Eq[SeqexecPages] = Eq.instance {
      case (Root, Root)                                               => true
      case (SoundTest, SoundTest)                                     => true
      case (InstrumentPage(i), InstrumentPage(j))                     => i === j
      case (SequencePage(i, o, s), SequencePage(j, p, r))             => i === j && o === p && s === r
      case (SequenceConfigPage(i, o, s), SequenceConfigPage(j, p, r)) => i === j && o === p && s === r
      case _                                                          => false
    }
  }

  // UI model
  sealed trait SectionVisibilityState
  case object SectionOpen extends SectionVisibilityState
  case object SectionClosed extends SectionVisibilityState

  object SectionVisibilityState {
    implicit val eq: Eq[SectionVisibilityState] = Eq.fromUniversalEquals
  }

  implicit class SectionVisibilityStateOps(val s: SectionVisibilityState) extends AnyVal {
    def toggle: SectionVisibilityState = s match {
      case SectionOpen   => SectionClosed
      case SectionClosed => SectionOpen
    }
  }

  final case class InstrumentTabActive(tab: SequenceTab, active: Boolean)

  object InstrumentTabActive {
    implicit val eq: Eq[InstrumentTabActive] =
      Eq[(SequenceTab, Boolean)].contramap(x => (x.tab, x.active))
  }

  final case class SequenceTab(instrument: Instrument, currentSequence: RefTo[Option[SequenceView]], completedSequence: Option[SequenceView], stepConfigDisplayed: Option[Int]) {
    // Returns the current sequence or if empty the last completed one
    // This must be a def since it will do a call to dereference a RefTo
    def sequence: Option[SequenceView] = currentSequence().orElse(completedSequence)
  }

  object SequenceTab {
    implicit val eq: Eq[SequenceTab] =
      Eq[(Instrument, RefTo[Option[SequenceView]], Option[SequenceView], Option[Int])].contramap(x => (x.instrument, x.currentSequence, x.completedSequence, x.stepConfigDisplayed))
    val empty: SequenceTab = SequenceTab(Instrument.F2, RefTo(new RootModelR(None)), None, None)
  }

  // Model for the tabbed area of sequences
  final case class SequencesOnDisplay(instrumentSequences: Zipper[SequenceTab]) {
    def withSite(site: SeqexecSite): SequencesOnDisplay =
      SequencesOnDisplay(Zipper.fromNel(site.instruments.map(SequenceTab(_, SequencesOnDisplay.emptySeqRef, None, None))))

    // Display a given step on the focused sequence
    def showStep(i: Int): SequencesOnDisplay =
      copy(instrumentSequences = instrumentSequences.modify(_.copy(stepConfigDisplayed = Some(i))))

    // Don't show steps for the sequence
    def unshowStep: SequencesOnDisplay =
      copy(instrumentSequences = instrumentSequences.modify(_.copy(stepConfigDisplayed = None)))

    def focusOnSequence(s: RefTo[Option[SequenceView]]): SequencesOnDisplay = {
      // Replace the sequence for the instrument or the completed sequence
      val q = instrumentSequences.findFocus(i => s().exists(_.metadata.instrument === i.instrument)).modify(_.copy(currentSequence = s))
      copy(instrumentSequences = q)
    }

    def focusOnInstrument(i: Instrument): SequencesOnDisplay = {
      // Focus on the instrument
      val q = instrumentSequences.findFocus(s => s.instrument === i)
      copy(instrumentSequences = q)
    }

    def isAnySelected: Boolean = instrumentSequences.exists(_.sequence.isDefined)

    // Is the id on the sequences area?
    def idDisplayed(id: SequenceId): Boolean =
      instrumentSequences.withFocus.exists { case (s, a) => a && s.sequence.exists(_.id === id) }

    def instrument(i: Instrument): InstrumentTabActive =
      // The getOrElse shouldn't be called as we have an element per instrument
      instrumentSequences.withFocus.find(_._1.instrument === i)
        .map{ case (i, a) => InstrumentTabActive(i, a) }.getOrElse(InstrumentTabActive(SequenceTab.empty, active = false))

    // We'll set the passed SequenceView as completed for the given instruments
    def markCompleted(completed: SequenceView): SequencesOnDisplay = {
      val q = instrumentSequences.findFocus(s => s.instrument === completed.metadata.instrument).modify(_.copy(completedSequence = completed.some))
      copy(instrumentSequences = q)
    }
  }

  /**
    * Contains the sequences displayed on the instrument tabs. Note that they are references to sequences on the Queue
    */
  object SequencesOnDisplay {
    val emptySeqRef: RefTo[Option[SequenceView]] = RefTo(new RootModelR(None))

    // We need to initialize the model with some instruments but it will be shortly replaced by the actual list
    val empty: SequencesOnDisplay = SequencesOnDisplay(Zipper.fromNel(Instrument.gsInstruments.map(SequenceTab(_, emptySeqRef, None, None))))
  }

  final case class WebSocketConnection(ws: Pot[WebSocket], nextAttempt: Int, autoReconnect: Boolean)

  object WebSocketConnection {
    val empty: WebSocketConnection = WebSocketConnection(Empty, 0, autoReconnect = true)

    implicit val equal: Eq[WebSocketConnection] =
      Eq[(Pot[WebSocket], Int, Boolean)].contramap { x =>
        (x.ws, x.nextAttempt, x.autoReconnect)
      }

  }

  /**
    * Keeps a list of log entries for display
    */
  final case class GlobalLog(log: FixedLengthBuffer[ServerLogMessage], display: SectionVisibilityState)

  /**
   * Model to display a resource conflict
   */
  final case class ResourcesConflict(visibility: SectionVisibilityState, id: Option[SequenceId])

  /**
   * UI model, changes here will update the UI
   */
  final case class SeqexecUIModel(navLocation: Pages.SeqexecPages,
                            user: Option[UserDetails],
                            sequences: SeqexecAppRootModel.LoadedSequences,
                            loginBox: SectionVisibilityState,
                            resourceConflict: ResourcesConflict,
                            globalLog: GlobalLog,
                            sequencesOnDisplay: SequencesOnDisplay,
                            firstLoad: Boolean)

  object SeqexecUIModel {
    val noSequencesLoaded: SequencesQueue[SequenceView] = SequencesQueue[SequenceView](Conditions.default, None, Nil)
    val initial: SeqexecUIModel = SeqexecUIModel(Pages.Root, None, noSequencesLoaded,
      SectionClosed, ResourcesConflict(SectionClosed, None), GlobalLog(FixedLengthBuffer.unsafeFromInt(500), SectionClosed), SequencesOnDisplay.empty, firstLoad = true)
  }

  /**
    * Root of the UI Model of the application
    */
  final case class SeqexecAppRootModel(ws: WebSocketConnection, site: Option[SeqexecSite], clientId: Option[ClientID], uiModel: SeqexecUIModel)

  object SeqexecAppRootModel {
    type LoadedSequences = SequencesQueue[SequenceView]

    val initial: SeqexecAppRootModel = SeqexecAppRootModel(WebSocketConnection.empty, None, None, SeqexecUIModel.initial)
  }
}

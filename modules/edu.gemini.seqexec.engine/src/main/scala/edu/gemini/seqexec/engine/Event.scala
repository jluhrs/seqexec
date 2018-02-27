// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.engine

import scalaz.syntax.std.option._
import edu.gemini.seqexec.model.Model.Observer
import edu.gemini.seqexec.model.UserDetails

import scalaz.concurrent.Task
import scalaz.stream.Process

/**
  * Anything that can go through the Event Queue.
  */
sealed abstract class Event[+D<:Engine.Types]
final case class EventUser[D<:Engine.Types](ue: UserEvent[D]) extends Event[D]
final case class EventSystem(se: SystemEvent) extends Event[Nothing]

object Event {

  def start[D<:Engine.Types](id: Sequence.Id, user: UserDetails): Event[D] = EventUser[D](Start(id, user.some))
  def pause[D<:Engine.Types](id: Sequence.Id, user: UserDetails): Event[D] = EventUser[D](Pause(id, user.some))
  def cancelPause[D<:Engine.Types](id: Sequence.Id, user: UserDetails): Event[D] = EventUser[D](CancelPause(id, user.some))
  def load[D<:Engine.Types](id: Sequence.Id, sequence: Sequence): Event[D] = EventUser[D](Load(id, sequence))
  def unload[D<:Engine.Types](id: Sequence.Id): Event[D] = EventUser[D](Unload(id))
  def breakpoint[D<:Engine.Types](id: Sequence.Id, user: UserDetails, step: Step.Id, v: Boolean): Event[D] = EventUser[D](Breakpoint(id, user.some, step, v))
  def skip[D<:Engine.Types](id: Sequence.Id, user: UserDetails, step: Step.Id, v: Boolean): Event[D] = EventUser[D](SkipMark(id, user.some, step, v))
  def setObserver[D<:Engine.Types](id: Sequence.Id, user: UserDetails, name: Observer): Event[D] = EventUser[D](SetObserver(id, user.some, name))
  val poll: Event[Nothing] = EventUser(Poll)
  def getState[D<:Engine.Types](f: (Engine.State[D#StateData]) => Task[Option[Process[Task, Event[D]]]]): Event[D] = EventUser[D](GetState[D](f))
  def modifyState[D<:Engine.Types](f: (Engine.State[D#StateData]) => Engine.State[D#StateData], data: D#EventData): Event[D] = EventUser[D](ModifyState[D](f, data))
  def getSeqState[D<:Engine.Types](id: Sequence.Id, f: (Sequence.State) => Option[Process[Task, Event[D]]]): Event[D] = EventUser[D](GetSeqState(id, f))
  def actionStop[D<:Engine.Types](id: Sequence.Id, f: (Sequence.State) => Option[Process[Task, Event[D]]]): Event[D] = EventUser[D](ActionStop(id, f))
  def actionResume[D<:Engine.Types](id: Sequence.Id, i: Int, c: Task[Result]): Event[D] = EventUser[D](ActionResume(id, i, c))
  def logDebugMsg[D<:Engine.Types](msg: String): Event[D] = EventUser[D](LogDebug(msg))
  def logInfoMsg[D<:Engine.Types](msg: String): Event[D] = EventUser[D](LogInfo(msg))
  def logWarningMsg[D<:Engine.Types](msg: String): Event[D] = EventUser[D](LogWarning(msg))
  def logErrorMsg[D<:Engine.Types](msg: String): Event[D] = EventUser[D](LogError(msg))

  def failed(id: Sequence.Id, i: Int, e: Result.Error): Event[Nothing] = EventSystem(Failed(id, i, e))
  def completed[R<:Result.RetVal](id: Sequence.Id, i: Int, r: Result.OK[R]): Event[Nothing] = EventSystem(Completed(id, i, r))
  def partial[R<:Result.PartialVal](id: Sequence.Id, i: Int, r: Result.Partial[R]): Event[Nothing] = EventSystem(PartialResult(id, i, r))
  def paused[C <: Result.PauseContext](id: Sequence.Id, i: Int, c: Result.Paused[C]): Event[Nothing] = EventSystem(Paused(id, i, c))
  def breakpointReached(id: Sequence.Id): Event[Nothing] = EventSystem(BreakpointReached(id))
  def busy(id: Sequence.Id): Event[Nothing] = EventSystem(Busy(id))
  def executed(id: Sequence.Id): Event[Nothing] = EventSystem(Executed(id))
  def executing(id: Sequence.Id): Event[Nothing] = EventSystem(Executing(id))
  def finished(id: Sequence.Id): Event[Nothing] = EventSystem(Finished(id))
  def nullEvent: Event[Nothing] = EventSystem(Null)

}

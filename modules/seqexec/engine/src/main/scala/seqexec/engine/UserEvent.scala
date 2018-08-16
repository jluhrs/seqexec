// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.engine

import cats.effect.IO
import cats.implicits._
import fs2.Stream
import gem.Observation
import seqexec.model.Model.ClientID
import seqexec.model.UserDetails

/**
  * Events generated by the user.
  */
sealed trait UserEvent[+D<:Engine.Types] {
  def user: Option[UserDetails]
  def username: String = user.foldMap(_.username)
}

final case class Start(id: Observation.Id, user: Option[UserDetails], clientId: ClientID) extends UserEvent[Nothing]
final case class Pause(id: Observation.Id, user: Option[UserDetails]) extends UserEvent[Nothing]
final case class CancelPause(id: Observation.Id, user: Option[UserDetails]) extends UserEvent[Nothing]
final case class Breakpoint(id: Observation.Id, user: Option[UserDetails], step: Step.Id, v: Boolean) extends UserEvent[Nothing]
final case class SkipMark(id: Observation.Id, user: Option[UserDetails], step: Step.Id, v: Boolean) extends UserEvent[Nothing]
final case class Poll(clientId: ClientID) extends UserEvent[Nothing] {
  val user: Option[UserDetails] = None
}
// Generic event to put a function in the main Stream process, which takes an
// action depending on the current state
final case class GetState[D<:Engine.Types](f: D#StateType => Option[Stream[IO, Event[D]]]) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
// Generic event to put a function in the main Process process, which changes the state
// depending on the current state
final case class ModifyState[D<:Engine.Types](f: D#StateType => D#StateType, event: D#EventData) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
final case class ModifyStateF[D <: Engine.Types](f: D#StateType => D#StateType, event: D#StateType => D#EventData) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}
// Calls a user given function in the main Stream process to stop an Action.
// It sets the Sequence to be stopped. The user function is called only if the Sequence is running.
final case class ActionStop[D <: Engine.Types](id: Observation.Id, f: D#StateType => Option[Stream[IO, Event[D]]]) extends UserEvent[D] {
  val user: Option[UserDetails] = None
}

// Uses `cont` to resume execution of a paused Action. If the Action is not paused, it does nothing.
final case class ActionResume(id: Observation.Id, i: Int, cont: IO[Result]) extends UserEvent[Nothing] {
  val user: Option[UserDetails] = None
}

final case class LogDebug(msg: String) extends UserEvent[Nothing] {
  val user: Option[UserDetails] = None
}

final case class LogInfo(msg: String) extends UserEvent[Nothing] {
  val user: Option[UserDetails] = None
}

final case class LogWarning(msg: String) extends UserEvent[Nothing] {
  val user: Option[UserDetails] = None
}

final case class LogError(msg: String) extends UserEvent[Nothing] {
  val user: Option[UserDetails] = None
}

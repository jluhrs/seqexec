package edu.gemini.seqexec.web.client.model

import java.util.logging.{Level, Logger}

import diode.data._
import diode.react.ReactConnector
import diode.util.RunAfterJS
import diode._
import edu.gemini.seqexec.model._
import edu.gemini.seqexec.web.client.model.SeqexecCircuit.SearchResults
import edu.gemini.seqexec.web.client.services.log.ConsoleHandler
import edu.gemini.seqexec.web.client.services.{Audio, SeqexecWebClient}
import edu.gemini.seqexec.web.common.{SeqexecQueue, Sequence}
import org.scalajs.dom._
import boopickle.Default._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scalaz.{-\/, \/, \/-}

// Action Handlers
/**
  * Handles actions related to the queue like loading and adding new elements
  */
class QueueHandler[M](modelRW: ModelRW[M, Pot[SeqexecQueue]]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case action: UpdatedQueue =>
      // Request loading the queue with ajax
      val loadEffect = action.effect(SeqexecWebClient.readQueue())(identity)
      action.handleWith(this, loadEffect)(PotAction.handler(250.milli))

    case AddToQueue(s) =>
      // Append to the current queue if not in the queue already
      val logE = SeqexecCircuit.appendToLogE(s"Sequence ${s.id} added to the queue")
      val u = value.map(u => u.copy((s :: u.queue.filter(_.id != s.id)).reverse))
      updated(u, logE)
  }
}

/**
  * Handles actions related to search
  */
class SearchHandler[M](modelRW: ModelRW[M, Pot[SeqexecCircuit.SearchResults]]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case action: SearchSequence =>
      // Request loading the queue with ajax
      val loadEffect = action.effect(SeqexecWebClient.read(action.criteria))(identity)
      action.handleWith(this, loadEffect)(PotAction.handler(250.milli))
    case RemoveFromSearch(s) =>
      val empty = value.map(l => l.size == 1 && l.exists(_.id == s.id)).getOrElse(false)
      // TODO, this should be an effect, but somehow it breaks the queue tracking
      if (empty) {
        SeqexecCircuit.dispatch(CloseSearchArea)
      }
      updated(value.map(_.filterNot(_ == s)))
  }
}

/**
  * Handles sequence execution actions
  */
class SequenceExecutionHandler[M](modelRW: ModelRW[M, Pot[SeqexecQueue]]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case RequestRun(s) =>
      effectOnly(Effect(SeqexecWebClient.run(s).map(r => if (r.error) RunStartFailed(s) else RunStarted(s))))

    case RequestStop(s) =>
      effectOnly(Effect(SeqexecWebClient.stop(s).map(r => if (r.error) RunStopFailed(s) else RunStopped(s))))

    // We could react to these events but we rather wait for the command from the event queue
    case RunStarted(s) =>
      noChange

    case RunStartFailed(s) =>
      noChange

    case RunStopped(s) =>
      // Normally we'd like to wait for the event queue to send us a stop, but that isn't yet working, so this will do
      val logE = SeqexecCircuit.appendToLogE(s"Sequence ${s.id} aborted")
      updated(value.map(_.abortSequence(s.id)), logE)

    case RunStopFailed(s) =>
      noChange
  }
}

/**
  * Handles actions related to the search area, used to open/close the area
  */
class SearchAreaHandler[M](modelRW: ModelRW[M, SectionVisibilityState]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case OpenSearchArea  =>
      updated(SectionOpen)

    case CloseSearchArea =>
      updated(SectionClosed)
  }
}

/**
  * Handles actions related to the development console
  */
class DevConsoleHandler[M](modelRW: ModelRW[M, SectionVisibilityState]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case ToggleDevConsole if value == SectionOpen   =>
      updated(SectionClosed)

    case ToggleDevConsole if value == SectionClosed =>
      updated(SectionOpen)
  }
}

/**
  * Handles actions related to opening/closing the login box
  */
class LoginBoxHandler[M](modelRW: ModelRW[M, SectionVisibilityState]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case OpenLoginBox if value == SectionClosed =>
      updated(SectionOpen)

    case OpenLoginBox                           =>
      noChange

    case CloseLoginBox if value == SectionOpen  =>
      updated(SectionClosed)

    case CloseLoginBox                          =>
      noChange
  }
}

/**
  * Handles actions related to opening/closing the login box
  */
class UserLoginHandler[M](modelRW: ModelRW[M, Option[UserDetails]]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case LoggedIn(u) =>
      // Close the login box
      val effect = Effect(Future(CloseLoginBox))
      updated(Some(u), effect)

    case Logout =>
      val effect = Effect(SeqexecWebClient.logout().map(_ => NoAction))
      // Remove the user and call logout
      updated(None, effect)
  }
}

/**
  * Handles actions related to the changing the selection of the displayed sequence
  */
class SequenceDisplayHandler[M](modelRW: ModelRW[M, SequencesOnDisplay]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case SelectToDisplay(s) =>
      val ref = SeqexecCircuit.sequenceRef(s.id)
      updated(value.focusOnSequence(ref))

    case ShowStep(s, i) =>
      if (value.instrumentSequences.focus.sequence().exists(_.id == s.id)) {
        updated(value.showStep(i))
      } else {
        noChange
      }

    case UnShowStep(s) =>
      if (value.instrumentSequences.focus.sequence().exists(_.id == s.id)) {
        updated(value.unshowStep)
      } else {
        noChange
      }
  }
}

/**
  * Handles updates to the log
  */
class GlobalLogHandler[M](modelRW: ModelRW[M, GlobalLog]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case AppendToLog(s) =>
      updated(value.append(s))
  }
}

/**
  * Handles the WebSocket connection and performs reconnection if needed
  */
class WebSocketHandler[M](modelRW: ModelRW[M, Option[WebSocket]]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS
  val logger = Logger.getLogger(this.getClass.getSimpleName)
  // Reconfigure to avoid sending ajax events in this logger
  logger.setUseParentHandlers(false)
  logger.addHandler(new ConsoleHandler(Level.FINE))

  // Makes a websocket connection and setups event listeners
  def webSocket(nextDelay: Int) = Future[Action] {
    import org.scalajs.dom.document

    val host = document.location.host
    val url = s"ws://$host/api/seqexec/events"

    def onOpen(e: Event): Unit = {
      logger.info(s"Connected to $url")
      SeqexecCircuit.dispatch(Connected)
    }

    def onMessage(e: MessageEvent): Unit = {
      \/.fromTryCatchNonFatal(read[SeqexecEvent](e.data.toString)) match {
        case \/-(event) => SeqexecCircuit.dispatch(NewSeqexecEvent(event))
        case -\/(t)     => println(s"Error decoding event ${t.getMessage}")
      }
    }

    def onError(e: ErrorEvent): Unit = {
      // Unfortunately reading the event is not cross-browser safe
      logger.severe("Error on websocket")
    }

    def onClose(e: CloseEvent): Unit =
      // Increase the delay to get exponential backoff with a minimum of 200ms and a max of 1m
      SeqexecCircuit.dispatch(ConnectionClosed(math.min(60000, math.max(200, nextDelay * 2))))

    val ws = new WebSocket(url)
    ws.onopen = onOpen _
    ws.onmessage = onMessage _
    ws.onerror = onError _
    ws.onclose = onClose _
    Connecting(ws)
  }.recover {
    case e: Throwable => NoAction
  }

  // This is essentially a state machine to handle the connection status and
  // can reconnect if needed
  override protected def handle = {
    case WSConnect(d) =>
      effectOnly(Effect(webSocket(d)).after(d.millis))

    case Connecting(ws) =>
      updated(Some(ws))

    case Connected =>
      effectOnly(Effect.action(AppendToLog("Connected")))

    case ConnectionError(e) =>
      effectOnly(Effect.action(AppendToLog(e)))

    case ConnectionClosed(d) =>
      logger.fine("Retry connecting in "+ d)
      val effect = Effect(Future(WSConnect(d)))
      updated(None, effect)
  }
}

/**
  * Handles messages received over the WS channel
  */
class WebSocketEventsHandler[M](modelRW: ModelRW[M, (Pot[SeqexecQueue], WebSocketsLog, Option[UserDetails])]) extends ActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle = {
    case NewSeqexecEvent(SeqexecConnectionOpenEvent(u)) =>
      updated(value.copy(_3 = u))

    case NewSeqexecEvent(event @ SequenceStartEvent(id)) =>
      val logE = SeqexecCircuit.appendToLogE(s"Sequence $id started")
      updated(value.copy(_1 = value._1.map(_.sequenceRunning(id)), _2 = value._2.append(event)), logE)

    case NewSeqexecEvent(event @ StepExecutedEvent(id, c, _, f)) =>
      val logE = SeqexecCircuit.appendToLogE(s"Sequence $id, step $c completed")
      updated(value.copy(_1 = value._1.map(_.markStepAsCompleted(id, c, f)), _2 = value._2.append(event)), logE)

    case NewSeqexecEvent(event @ SequenceCompletedEvent(id)) =>
      val audioEffect = Effect(Future(new Audio("/sequencecomplete.mp3").play()).map(_ => NoAction))
      val logE = SeqexecCircuit.appendToLogE(s"Sequence $id completed")
      updated(value.copy(_1 = value._1.map(_.sequenceCompleted(id)), _2 = value._2.append(event)), audioEffect >> logE)

    case NewSeqexecEvent(s) =>
      // Ignore unknown events
      noChange
  }
}

/**
  * Generates Eq comparisons for Pot[A], it is useful for state indicators
  */
object PotEq {
  def potStateEq[A]: FastEq[Pot[A]] = new FastEq[Pot[A]] {
    override def eqv(a: Pot[A], b: Pot[A]): Boolean = a.state == b.state
  }

  val seqexecQueueEq = potStateEq[SeqexecQueue]
  val searchResultsEq = potStateEq[SearchResults]
}

/**
  * Contains the model for Diode
  */
object SeqexecCircuit extends Circuit[SeqexecAppRootModel] with ReactConnector[SeqexecAppRootModel] {
  // Import the correct picklers
  import SeqexecEvent._
  type SearchResults = List[Sequence]

  val logger = Logger.getLogger(SeqexecCircuit.getClass.getSimpleName)

  def appendToLogE(s: String) =
    Effect(Future(AppendToLog(s)))

  val wsHandler              = new WebSocketHandler(zoomRW(_.ws)((m, v) => m.copy(ws = v)))
  // TODO Make into its own class
  val webSocket = {
    import org.scalajs.dom.document

    val host = document.location.host

    def onOpen(e: Event): Unit = {
      dispatch(AppendToLog("Connected"))
      dispatch(ConnectionOpened)
    }

    def onMessage(e: MessageEvent): Unit = {
      val byteBuffer = TypedArrayBuffer.wrap(e.data.asInstanceOf[ArrayBuffer])
      \/.fromTryCatchNonFatal(Unpickle[SeqexecEvent].fromBytes(byteBuffer)) match {
        case \/-(event) => dispatch(NewSeqexecEvent(event))
        case -\/(t)     => println(s"Error decoding event ${t.getMessage}")
      }
    }

    def onError(e: ErrorEvent): Unit = {
      println("Error " + e)
      dispatch(ConnectionError(e.message))
    }

    def onClose(e: CloseEvent): Unit = {
      println("Close ")
      dispatch(ConnectionClosed)
    }

    val ws = new WebSocket(s"ws://$host/api/seqexec/events")
    ws.binaryType = "arraybuffer"
    ws.onopen = onOpen _
    ws.onmessage = onMessage _
    ws.onerror = onError _
    ws.onclose = onClose _
    Some(ws)
  }

  val queueHandler           = new QueueHandler(zoomRW(_.queue)((m, v) => m.copy(queue = v)))
  val searchHandler          = new SearchHandler(zoomRW(_.searchResults)((m, v) => m.copy(searchResults = v)))
  val searchAreaHandler      = new SearchAreaHandler(zoomRW(_.searchAreaState)((m, v) => m.copy(searchAreaState = v)))
  val devConsoleHandler      = new DevConsoleHandler(zoomRW(_.devConsoleState)((m, v) => m.copy(devConsoleState = v)))
  val loginBoxHandler        = new LoginBoxHandler(zoomRW(_.loginBox)((m, v) => m.copy(loginBox = v)))
  val userLoginHandler       = new UserLoginHandler(zoomRW(_.user)((m, v) => m.copy(user = v)))
  val wsLogHandler           = new WebSocketEventsHandler(zoomRW(m => (m.queue, m.webSocketLog, m.user))((m, v) => m.copy(queue = v._1, webSocketLog = v._2, user = v._3)))
  val sequenceDisplayHandler = new SequenceDisplayHandler(zoomRW(_.sequencesOnDisplay)((m, v) => m.copy(sequencesOnDisplay = v)))
  val sequenceExecHandler    = new SequenceExecutionHandler(zoomRW(_.queue)((m, v) => m.copy(queue = v)))
  val globalLogHandler       = new GlobalLogHandler(zoomRW(_.globalLog)((m, v) => m.copy(globalLog = v)))

  override protected def initialModel = SeqexecAppRootModel.initial

  // Reader for a specific sequence if available
  def sequenceReader(id: String):ModelR[_, Pot[Sequence]] =
    zoomFlatMap(_.queue)(_.queue.find(_.id == id).fold(Empty: Pot[Sequence])(s => Ready(s)))

  /**
    * Makes a reference to a sequence on the queue.
    * This way we have a normalized model and need to update it in only one place
    */
  def sequenceRef(id: String):RefTo[Pot[Sequence]] =
    RefTo(sequenceReader(id))

  override protected def actionHandler = composeHandlers(
    wsHandler,
    queueHandler,
    searchHandler,
    searchAreaHandler,
    devConsoleHandler,
    loginBoxHandler,
    userLoginHandler,
    wsLogHandler,
    sequenceDisplayHandler,
    globalLogHandler,
    sequenceExecHandler)

  /**
    * Handles a fatal error most likely during action processing
    */
  override def handleFatal(action: Any, e: Throwable): Unit = {
    logger.severe(s"Action not handled $action")
    super.handleFatal(action, e)
  }

  /**
    * Handle a non-fatal error, such as dispatching an action with no action handler.
    */
  override def handleError(msg: String): Unit = {
    logger.severe(s"Action error $msg")
    throw new Exception(s"handleError called with: $msg")
  }

}

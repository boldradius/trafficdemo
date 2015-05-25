import java.net.InetSocketAddress

import akka.actor._
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import akka.util.ByteString

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Main program
 */
object TCPClientApp extends App {
  val host = if (args.size > 0) args(0) else "localhost"
  val port = if (args.size > 1) Integer.parseInt(args(1)) else 4200
  val numClients = if (args.size > 2) Integer.parseInt(args(2)) else 1
  val clientManager: ActorRef = ActorSystem().actorOf(Props(classOf[TCPClientManager], new InetSocketAddress(host, port), numClients))
  clientManager ! StartSession
}

/**
 * Settings
 */
object SessionConfig {
  val SESSION_DURATION = 30 seconds
  val PAUSE_DURATION = 10 seconds
}

/**
 * Clients manager actor.
 *
 */
class TCPClientManager(remoteAddr: InetSocketAddress, numClients: Int) extends Actor with ActorLogging {
  import context.dispatcher

  val statistics = context.actorOf(Statistics.props(numClients))

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    self ! StartSession
  }

  val router = Router(RoundRobinRoutingLogic(), Vector.fill(numClients) {
    val r = context.actorOf(Props(classOf[TCPClient], remoteAddr, numClients, statistics))
    context watch r
    ActorRefRoutee(r)
  })

  def receive = {
    case StartSession =>
      statistics ! Reset
      router.routees.foreach(_.send(InitConnection, self))
      context.system.scheduler.scheduleOnce(SessionConfig.SESSION_DURATION, self, EndSession)

    case EndSession =>
      router.routees.foreach(_.send(CloseConnection, self))
      context.system.scheduler.scheduleOnce(SessionConfig.PAUSE_DURATION, self, StartSession)

    case RestartSession =>
      statistics ! WriteLog
      self ! StartSession
  }
}

/**
 * Client actor
 *
 */
class TCPClient(remoteAddr: InetSocketAddress, numClients: Int, statistics: ActorRef) extends Actor with ActorLogging {
  import context.system
  import context.dispatcher

  private var requestResponseBalance = 0


  def receive = {
    case InitConnection =>
      IO(Tcp) ! Connect(remoteAddr)

    case CommandFailed(_: Connect) =>
      statistics ! RegisterConnectionFailure

    case c@Connected(remote, local) =>

      val connection = sender()
      connection ! Register(self)

      val tickScheduler = context.system.scheduler.schedule(0 seconds, 1 second, self, Tick)

      context become {
        case data: ByteString =>
          connection ! Write(data)
        case Tick =>
          connection ! Write(ByteString("echo"))
          requestResponseBalance = requestResponseBalance + 1
        case CommandFailed(w: Write) =>
          statistics ! RegisterWriteFailure
        case Received(data) =>
          requestResponseBalance = requestResponseBalance - 1
        case CloseConnection =>
          connection ! Close
        case _: ConnectionClosed =>
          if (requestResponseBalance > 0) statistics ! ReportLostResponses(requestResponseBalance)
          tickScheduler.cancel()
          context.unbecome()
      }
  }
}

case object Tick

case object StartSession

case object RestartSession

case object EndSession

case object InitConnection

case object CloseConnection

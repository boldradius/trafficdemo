import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Tcp}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.config.ConfigFactory

object TCPServerApp extends App {
    val customConf = ConfigFactory.parseString("""
akka.log-dead-letters = 0
akka.loglevel = DEBUG
""")

  val system = ActorSystem("server", ConfigFactory.load(customConf))
  system.actorOf(Props[TCPServer])
}

class TCPServer extends Actor with ActorLogging {
  var activeConnections = 0
  var processedRequests = 0

  import akka.io.Tcp._
  import context.system

  val TCPPort = 4200

  IO(Tcp) ! Bind(self, new InetSocketAddress(TCPPort))


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    system.scheduler.schedule(0 milliseconds,
      5 seconds,
      self,
      PrintStatistics)
    super.preStart()
  }

  def receive = {
    case b @ Bound(addr) =>
      log.info("Bound To Port '{}' on address '{}'", TCPPort, addr)

    case CommandFailed(_: Bind) =>
      log.error("Binding Command Failed. Exiting.")
      context stop self

    case c @ Connected(remote, local) =>
      activeConnections += 1
      val handler = context.actorOf(Props[TCPHandler])
      val connection = sender()
      connection ! Register(handler)

    case Processed => processedRequests += 1
    case ClosedConnection => activeConnections -= 1
    case PrintStatistics => log.info(s"active connections: ${activeConnections}  |  processed requests: ${processedRequests}")
  }

}

class TCPHandler extends Actor with ActorLogging {

  import akka.io.Tcp._

  def receive = {
    case Received(data) =>
      // For now, echo back to the client
      sender() ! Write(data)
      context.parent ! Processed
    case PeerClosed =>
      context.parent ! ClosedConnection
      context stop self
  }
}

case object ClosedConnection

case object Processed

case object PrintStatistics

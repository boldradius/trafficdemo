import akka.actor.{Props, Actor, ActorLogging}

/**
 * @author Dmitri Carpov
 */
case object Statistics {
  def props(expectedNumberOfConnections: Int) = Props(new Statistics(expectedNumberOfConnections))
}

class Statistics(expectedNumberOfConnections: Int) extends Actor with ActorLogging {
  private var connections = 0
  private var connectionEvents = 0
  private var connectionFailures = 0
  private var connectionsTime = -1.0
  private var writeFailures = -1.0
  private var startTime = System.currentTimeMillis()
  private var lostResponses = 0

  override def receive: Receive = {
    case WriteLog =>
      val logMessage = s"Session Log: |Connections established: ${connections} in ${connectionsTime}ms| Connections failed: ${connectionFailures} |Write failures: ${writeFailures} |Lost responses: ${lostResponses}"
      log.info(logMessage)

    case Reset =>
      // reset
      connections = 0
      connectionEvents = 0
      connectionFailures = 0
      connectionsTime = -1.0
      writeFailures = 0
      startTime = System.currentTimeMillis()
      lostResponses = 0

    case RegisterConnection =>
      connectionEvents = connectionEvents + 1
      connections = connections + 1
      if (connectionEvents == expectedNumberOfConnections) {
        connectionsTime = System.currentTimeMillis() - startTime
      }

    case RegisterConnectionFailure =>
      connectionEvents = connectionEvents + 1
      connectionFailures = connectionFailures + 1

    case ReportLostResponses(numberOfLostResponses) =>
      lostResponses = lostResponses + numberOfLostResponses

    case RegisterWriteFailure =>
      writeFailures = writeFailures + 1
  }
}


case object RegisterConnection

case object RegisterConnectionFailure

case object RegisterWriteFailure

case class ReportLostResponses(numberOfLostResponses: Int)

case object WriteLog

case object Reset

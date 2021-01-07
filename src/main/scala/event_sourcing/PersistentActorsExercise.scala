package event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import sun.jvm.hotspot.HelloWorld.e

import scala.:+

object PersistentActorsExercise extends App {

  /*
     Persistent actor for a voting station
     Keep:
       - the citizens who voted
       - the poll: mapping between a candidate and the number of received votes so far
     The actor must be able to recover its state if it's shut down or restarted
   */
  type CandidateName = String
  type PID = String
  type NrVotes = Int
  case class Vote(citizenPID: PID, candidate: CandidateName)
  case object PrintResult


  class VotingActor extends PersistentActor with ActorLogging {
    var voted: List[String] = List.empty
    var votes: Map[String, Int] = Map.empty
    override def receiveRecover: Receive = {
      case Vote(pid,candidate) =>
        handleInternalState(candidate,pid)
        log.info(s"vote event record with pid:$pid for candidate:$candidate restored")

    }

    override def receiveCommand: Receive = {
      case  vote @ Vote(pid, _) =>
        if (voted.contains(pid)) {
          log.warning(s"$pid already voted!")
        } else {
          /*
         1) create the event
         2) persist the event
         3) handle a state change after persisting is successful
        */
          persist(event = vote) // COMMAND sourcing
          { e =>handleInternalState(e.candidate,pid = e.citizenPID)}
        }
      case PrintResult =>
        votes.foreach(vote => log.info(s"${vote._1} have ${vote._2} votes"))
    }

    private def handleInternalState(candidate: CandidateName, pid:PID): Unit = {
      voted = voted :+ pid
      val newVoteCount = votes.getOrElse(candidate, 0) + 1
      votes = votes + (candidate -> newVoteCount)
      log.info(s"$pid your vote has been has been received :) ")
    }

    override def persistenceId: String = "voting-actor"
  }

  val system = ActorSystem("votingSystemDemo")
  val voteSystem = system.actorOf(Props[VotingActor], "voteSystemActor")
//  voteSystem ! Vote("Aviad", "Pavel")
//  voteSystem ! Vote("Aviv", "Aviad")
//  voteSystem ! Vote("Pavel", "Aviad")
//  voteSystem ! Vote("Irena", "Aviad")
//  voteSystem ! Vote("Faiana", "Aviv")
  voteSystem ! Vote("Aviad", "Pavel")
  voteSystem ! PrintResult

}

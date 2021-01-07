package event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

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
  case class VoteSystemRecord(pid: PID,
                              candidate: CandidateName,
                              votesSoFar: Map[CandidateName, NrVotes],
                              votedSoFar: List[PID])

  class VotingActor extends PersistentActor with ActorLogging {
    var voted: List[String] = List.empty
    var votes: Map[String, Int] = Map.empty
    override def receiveRecover: Receive = {
      case VoteSystemRecord(pid, candidate, votesSoFar, votedSoFar) =>
        voted = votedSoFar :+ pid
        val newVoteCount = votesSoFar.getOrElse(candidate, 0) + 1
        votes = votesSoFar + (candidate -> newVoteCount)
        log.info(
          s"vote event record with pid:$pid for candidate:$candidate restored")

    }

    override def receiveCommand: Receive = {
      case Vote(pid, candidate) =>
        if (voted.contains(pid)) {
          log.info(s"sorry $pid you already voted!")
        } else {
          persist(
            event = VoteSystemRecord(pid,
                                     candidate,
                                     votesSoFar = votes,
                                     votedSoFar = voted)
          ) { e =>
            voted = e.votedSoFar :+ e.pid
            val newVoteCount = e.votesSoFar.getOrElse(candidate, 0) + 1
            votes = e.votesSoFar + (candidate -> newVoteCount)
            log.info(s"${e.pid} your vote has been has been received :) ")
          }
        }
      case PrintResult =>
        votes.foreach(vote => log.info(s"${vote._1} have ${vote._2} votes"))
    }

    override def persistenceId: String = "voting-actor"
  }

  val system = ActorSystem("votingSystemDemo")
  val voteSystem = system.actorOf(Props[VotingActor], "voteSystemActor")
//  voteSystem ! Vote("Aviad", "Pavel")
//  voteSystem ! Vote("Aviad", "Pavel")
//  voteSystem ! Vote("Aviv", "Aviad")
//  voteSystem ! Vote("Pavel", "Aviad")
//  voteSystem ! Vote("Irena", "Aviad")
//  voteSystem ! Vote("Faiana", "Aviv")
  voteSystem ! PrintResult

}

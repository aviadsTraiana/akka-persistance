package practices

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.NotUsed
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

object PersistenceQueryDemo extends App {

  val system: ActorSystem = ActorSystem(
    name = "PersistenceQueryDemo",
    config = ConfigFactory.load().getConfig("persistenceQuery")
  )

  // read journal
  val readJournal: CassandraReadJournal = PersistenceQuery(system)
    .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  // give me all persistence IDs
  val persistenceIds: Source[String, NotUsed] =
    readJournal.currentPersistenceIds() //current* make it not a inf stream

  //to use streams api like runForeach
  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  persistenceIds.runForeach { persistenceId =>
    println(s"Found persistence ID: $persistenceId")
  }

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "persistence-query-id-1"

    override def receiveCommand: Receive = {
      case m =>
        persist(m) { _ =>
          log.info(s"Persisted: $m")
        }
    }

    override def receiveRecover: Receive = {
      case e => log.info(s"Recovered: $e")
    }
  }

  val simpleActor =
    system.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  import system.dispatcher
  system.scheduler.scheduleOnce(5 seconds) {
    val message = "hello a second time"
    simpleActor ! message
  }

  // events by persistence ID
  val events: Source[EventEnvelope, NotUsed] =
    readJournal.eventsByPersistenceId(persistenceId = "persistence-query-id-1",
                                      fromSequenceNr = 0,
                                      toSequenceNr = Long.MaxValue)
  events.runForeach { event =>
    println(s"Read event: $event")
  }

  // events by tags
  val genres = Array("pop", "rock", "hip-hop", "jazz", "disco")
  case class Song(artist: String, title: String, genre: String)
  // command
  case class Playlist(songs: List[Song])
  // event
  case class PlaylistPurchased(id: Int, songs: List[Song])

  class MusicStoreCheckoutActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "music-store-checkout"

    var latestPlaylistId = 0

    override def receiveCommand: Receive = {
      case Playlist(songs) =>
        persist(PlaylistPurchased(latestPlaylistId, songs)) { _ =>
          log.info(s"User purchased: $songs")
          latestPlaylistId += 1
        }
    }

    override def receiveRecover: Receive = {
      case event @ PlaylistPurchased(id, _) =>
        log.info(s"Recovered: $event")
        latestPlaylistId = id
    }
  }

  class MusicStoreEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = "musicStore"

    override def toJournal(event: Any): Any = event match {
      case event @ PlaylistPurchased(_, songs) =>
        val genres = songs.map(_.genre).toSet
        Tagged(payload = event, tags = genres) //this is how we tag payload
      case event => event
    }
  }

  val checkoutActor: ActorRef =
    system.actorOf(Props[MusicStoreCheckoutActor], "musicStoreActor")

  val r = new Random
  for (_ <- 1 to 10) {
    val maxSongs = r.nextInt(5)
    val songs = for (i <- 1 to maxSongs) yield {
      val randomGenre = genres(r.nextInt(5))
      Song(s"Artist $i", s"My Love Song $i", randomGenre)
    }

    checkoutActor ! Playlist(songs.toList)
  }

  val rockPlaylists: Source[EventEnvelope, NotUsed] =
    readJournal.eventsByTag("rock", Offset.noOffset)
  rockPlaylists.runForeach { event =>
    println(s"Found a playlist with a rock song: $event")
  }

}

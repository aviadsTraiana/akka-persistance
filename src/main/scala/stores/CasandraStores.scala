package stores

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object CasandraStores extends App {

    val posgresStoresActorSystem = ActorSystem("casandraSystem", ConfigFactory.load().getConfig("cassandraDemo"))
    val persistentActor = posgresStoresActorSystem.actorOf(Props[SimplePersistentActor], "casandraPersistentActor")

    for (i <- 1 to 10) {
        persistentActor ! s"I love Akka [$i]"
    }
    persistentActor ! "print"
    persistentActor ! "snap"

    for (i <- 11 to 20) {
        persistentActor ! s"I love Akka [$i]"
    }


}

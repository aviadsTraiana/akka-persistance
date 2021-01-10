package stores

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object PostgresStores extends App {

    val posgresStoresActorSystem = ActorSystem("posgresStoresSystem", ConfigFactory.load().getConfig("postgresDemo"))
    val persistentActor = posgresStoresActorSystem.actorOf(Props[SimplePersistentActor], "posgresPersistentActor")

    for (i <- 1 to 10) {
        persistentActor ! s"I love Akka [$i]"
    }
    persistentActor ! "print"
    persistentActor ! "snap"

    for (i <- 11 to 20) {
        persistentActor ! s"I love Akka [$i]"
    }

}

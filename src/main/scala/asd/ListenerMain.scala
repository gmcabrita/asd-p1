package asd

import asd._
import asd.message._
import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config.ConfigFactory

import java.io.File

object ListenerMain extends App {
  val system = ActorSystem("REMOTE", ConfigFactory.parseFile(new File("src/main/resources/listener.conf")))

  system.actorOf(Props[Server], "server1")
  system.actorOf(Props[Server], "server2")
  system.actorOf(Props[Server], "server3")
  system.actorOf(Props[Server], "server4")
  system.actorOf(Props[Server], "server5")
  system.actorOf(Props[Server], "server6")
}
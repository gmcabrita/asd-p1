package asd

import asd._
import asd.message._
import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config.ConfigFactory

object ListenerMain extends App {
  val system = ActorSystem("REMOTE", ConfigFactory.load("listener"))
}
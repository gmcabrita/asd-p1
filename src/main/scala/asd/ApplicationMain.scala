package asd

import asd.evaluation.LocalNoFailureEvaluation
import asd._

import akka.actor.{ActorRef, Props, ActorSystem}

case class Tag(tagmax: Int, actor_ref: ActorRef) {
  // this > that
  def compareTo(that: Tag): Boolean = {
    this.tagmax > that.tagmax || (this.tagmax == that.tagmax && this.actor_ref.compareTo(that.actor_ref) > 0)
  }
}
case class TagValue(tag: Tag, value: String)

// Messages
case class Write(tagmax: Int, key: String, value: String)
case class ReadTag(key: String)
case class Read(key: String)
case class Ack()
case class Put(key: String, value: String)
case class Get(key: String)
case class GetResult(value: String)
case class Delay(ms: Int) // milliseconds

case class Timedout()
case class Start()

object KVStore extends App {
  implicit val system = ActorSystem("MAIN")

  val eval = system.actorOf(Props(new LocalNoFailureEvaluation(
    1000, // num keys
    12, // num clients
    12, // num servers
    7, // quorum
    12, // degree of replication
    192371441, // seed
    true, // linearizable?
    50000, // number of operations
    5 // number of injected faults
  )))

  eval ! Start
}
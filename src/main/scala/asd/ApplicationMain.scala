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

  // zipf
  // 1000 keys
  // read/write ratios: 90/10, 50/50, 10/90
  // spawn 1 / 4 / 8 / 12 clients
  // 12 servers in one machine
  val eval = system.actorOf(Props(new LocalNoFailureEvaluation(
    1000, // num keys
    12, // num clients
    12, // num servers
    3, // quorum
    5, // degree of replication
    (90, 10), // rw ratio
    192371441, // seed
    true // linearizable?
  )))

  eval ! Start
  //eval.run()
  //sys.exit(0)
}
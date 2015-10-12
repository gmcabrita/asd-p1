package asd

import asd.evaluation.LocalNoFailureEvaluation

import akka.actor.ActorRef

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

object KVStore extends App {
  // zipf
  // 1000 keys
  // read/write ratios: 90/10, 50/50, 10/90
  // spawn 1 / 4 / 8 / 12 clients
  // 12 servers in one machine
  val eval = new LocalNoFailureEvaluation(
    1000, // num keys
    1, // num clients
    12, // num servers
    4, // quorum
    7, // degree of replication
    (90, 10), // rw ratio
    192371441 // seed
  )

  eval.run()
  sys.exit(0)
}
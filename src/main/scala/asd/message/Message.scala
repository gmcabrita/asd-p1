package asd.message

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
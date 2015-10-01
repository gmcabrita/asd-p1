package asd

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ActorDSL._
import scala.collection.parallel.mutable.ParHashMap
import scala.collection.mutable.MutableList

case class Tag(tagmax: Int, actor_ref: ActorRef)
case class TagValue(tag: Tag, value: String)

// Messages
case class Write(tagmax: Int, key: String, value: String)
case class ReadTag(key: String)
case class Read(key: String)
case class Ack()
case class Put(key: String, value: String)
case class Get(key: String)

class Server extends Actor {
  val store = new ParHashMap[String, TagValue]
  val default_tag = Tag(-1, null)

  def receive = {
    case Write(tagmax, key, value) => {
      val old_tag = store.get(key) match {
        case Some(tv) => tv.tag
        case None => default_tag
      }

      if (tagmax > old_tag.tagmax || (tagmax == old_tag.tagmax && sender.compareTo(old_tag.actor_ref) > 0)) {
        val tv = TagValue(Tag(tagmax, sender), value)
        store.put(key, tv)
      }

      sender ! Ack
    }
    case ReadTag(key) => {
      val tag = store.get(key) match {
        case Some(tv) => tv.tag
        case None  => default_tag
      }

      sender ! tag
    }
    case Read(key) => {
      sender ! store.get(key)
    }
  }
}

class Client(servers: List[ActorRef], quorum: Int, degree_of_replication: Int) extends Actor {
  def receive = {
    case Put(key, value) => {
      implicit val system = ActorSystem("ASD")
      implicit val box = inbox()
      //TODO: use hash(key) % degree_of_replication to pick servers
      servers.foreach((s) => {
        box.send(s, ReadTag(key))
      })

      var highest_tagmax = -1
      for (i <- 1 to quorum) {
        //val msg = box.receive()
        val tagmax = box.select() {
          case Tag(tagmax, _) => highest_tagmax
        }
        if (tagmax > highest_tagmax) {
          highest_tagmax = tagmax
        }
      }

      servers.foreach((s) => {
        box.send(s, Write(highest_tagmax + 1, key, value))
      })

      for (i <- 1 to quorum) {
        box.select() {
          case Ack => ()
        }
      }

      Ack
    }
    case Get(key) => {
      // TODO: implement
    }
  }
}

object KVStore extends App {
  def main(args: List[String]) = {
  }
}

// 2. The second variant should provide linearizability.
// Make reads only take one phase
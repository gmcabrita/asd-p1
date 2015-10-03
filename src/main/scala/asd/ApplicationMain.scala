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
case class GetResult(value: String)

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
  def pick_servers(key: String): List[ActorRef] = {
    val start = Math.abs(key.hashCode() % degree_of_replication)
    var picked = servers.slice(start, start + degree_of_replication)
    if (picked.size < degree_of_replication) {
      picked ++= servers.slice(0, degree_of_replication - picked.size)
    }

    picked
  }

  def receive = {
    case Put(key, value) => {
      implicit val system = ActorSystem("ASD")
      implicit val box = inbox()
      val picked_servers = pick_servers(key)

      // send readtags to n servers
      picked_servers.foreach((s) => {
        box.send(s, ReadTag(key))
      })

      // wait for the quorum of answers
      var highest_tagmax = -1
      for (i <- 1 to quorum) {
        val tagmax = box.select() {
          case Tag(tm, _) => tm
        }

        // grab the highest tagmax
        if (tagmax > highest_tagmax) {
          highest_tagmax = tagmax
        }
      }

      // send writes to the n servers
      picked_servers.foreach((s) => {
        box.send(s, Write(highest_tagmax + 1, key, value))
      })

      // wait for quorum of acks
      for (i <- 1 to quorum) {
        box.select() {
          case Ack => ()
        }
      }

      Ack
    }
    case Get(key) => {
      implicit val system = ActorSystem("ASD")
      implicit val box = inbox()
      val picked_servers = pick_servers(key)

      // send reads to n servers
      picked_servers.foreach((s) => {
        box.send(s, Read(key))
      })

      // wait for quorum of answers
      var highest_tagvalue = TagValue(Tag(-1, null), null)
      for (i <- 1 to quorum) {
        val tagvalue = box.select() {
          case tv: TagValue => tv
        }

        // grab the highest <tagmax,valmax> tuple
        if (tagvalue.tag.tagmax > highest_tagvalue.tag.tagmax ||
            (tagvalue.tag.tagmax == highest_tagvalue.tag.tagmax &&
              tagvalue.tag.actor_ref.compareTo(highest_tagvalue.tag.actor_ref) > 0)) {
          highest_tagvalue = tagvalue
        }
      }

      // send writes to the n servers (for replicating)
      picked_servers.foreach((s) => {
        box.send(s, Write(highest_tagvalue.tag.tagmax, key, highest_tagvalue.value))
      })

      // wait for quorum of acks
      for (i <- 1 to quorum) {
        box.select() {
          case Ack => ()
        }
      }

      GetResult(highest_tagvalue.value)
    }
  }
}

object KVStore extends App {
  def main(args: List[String]) = {
    // TODO: launch the server and client actors
  }
}

// 2. The second variant should provide linearizability.
// Make reads only take one phase
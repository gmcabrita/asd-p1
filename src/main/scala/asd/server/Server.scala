package asd

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorDSL._
import scala.collection.parallel.mutable.ParHashMap

class Server extends Actor {
  var store = new ParHashMap[String, TagValue]

  def receive = {
    case Write(tagmax, key, value) => {
      store.get(key) match {
        case Some(TagValue(old_tag, _)) => {
          val tv = TagValue(Tag(tagmax, sender), value)
          if (tv.tag.compareTo(old_tag)) {
            store.put(key, tv)
          }
        }
        case None => {
          val tv = TagValue(Tag(tagmax, sender), value)
          store.put(key, tv)
        }
      }

      sender ! Ack
    }
    case ReadTag(key) => {
      val tag = store.get(key) match {
        case Some(tv) => Some(tv.tag)
        case None  => None
      }

      sender ! tag
    }
    case Read(key) => {
      sender ! store.get(key)
    }
  }
}
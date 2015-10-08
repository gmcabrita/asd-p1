package asd

import akka.actor.Actor
import akka.actor.ActorRef
import scala.collection.parallel.mutable.ParHashMap
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Server extends Actor {
  var store = new ParHashMap[String, TagValue]
  var stopped = false
  var delay: FiniteDuration = FiniteDuration(0, "millis")

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
      case object WakeUp
      val client = sender // avoid shadowing of sender inside context.become
      context.system.scheduler.scheduleOnce(delay, self, WakeUp)
      context.become({
        case WakeUp => context.unbecome()
        client ! store.get(key)
      }, discardOld = false)
    }

    // fault injection
    case Delay(ms) => {
      delay = FiniteDuration(ms, "millis")

      sender ! Ack
    }
  }
}
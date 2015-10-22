package asd

import asd.message._
import akka.actor.Actor
import akka.actor.ActorRef
import scala.collection.parallel.mutable.ParHashMap
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Server extends Actor {
  case object WakeUp
  var store = new ParHashMap[String, TagValue]
  var delay: FiniteDuration = FiniteDuration(0, "millis")

  /*def injectDelay(ops: () => Unit) = {
    context.system.scheduler.scheduleOnce(delay, self, WakeUp)
    context.become({
      case WakeUp => context.unbecome()
      ops()
    }, discardOld = false)
  }*/

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

    // on-demand response delays
    /*case Delay(ms) => {
      delay = FiniteDuration(ms, "millis")

      sender ! Ack
    }*/

    // fault-injection
    case Stop => {
      context.stop(self)
    }
  }
}
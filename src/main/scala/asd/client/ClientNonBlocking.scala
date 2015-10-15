package asd

import asd.message._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ActorDSL._
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import akka.event.Logging
import akka.event.LoggingAdapter

class ReadSender(server: ActorRef, reply_to: ActorRef, key: String) extends Actor {
  def receive = {
    case Start => server ! Read(key)
    case Some(tv: TagValue) => reply_to ! Some(tv)
    case None =>  reply_to ! None
  }
}

class ReadTagSender(server: ActorRef, reply_to: ActorRef, key: String) extends Actor {
  def receive = {
    case Start => server ! ReadTag(key)
    case Some(t: Tag) => reply_to ! Some(t)
    case None =>  reply_to ! None
  }
}

class WriteSender(server: ActorRef, reply_to: ActorRef, tagmax: Int, key: String, value: String) extends Actor {
  def receive = {
    case Start => server ! Write(tagmax, key, value)
    case Ack => reply_to ! Ack
  }
}

class ClientNonBlocking(servers: List[ActorRef], quorum: Int, degree_of_replication: Int) extends Actor {
  implicit val timeout = Timeout(5 seconds)
  val log = Logging.getLogger(context.system, this)

  def pick_servers(key: String): List[ActorRef] = {
    val start = Math.abs(key.hashCode() % degree_of_replication)

    val picked = servers.slice(start, start + degree_of_replication)
    if (picked.size < degree_of_replication) {
      picked ++ servers.slice(0, degree_of_replication - picked.size)
    } else {
      picked
    }
  }

  def waiting_for_replica(respond_to: ActorRef, received: Int, result: Option[GetResult]): Receive = {
    case Ack => {
      if (received + 1 == quorum) {
        respond_to ! result
        context.become(receive)
      } else {
        context.become(waiting_for_replica(respond_to, received + 1, result))
      }
    }
    case ReceiveTimeout => {
      log.warning("Timeout on replication. Was: {}, Received: {}, Result: {}", self, received, result)
      respond_to ! Timedout
      context.become(receive)
    }
  }

  def waiting_for_get_responses(picked_servers: List[ActorRef], respond_to: ActorRef, received: Int, key: String, highest: Option[TagValue] = None): Receive = {
    case Some(tv: TagValue) => {
      val tagvalue = highest match {
        case Some(old_tv: TagValue) => {
          if (tv.tag.compareTo(old_tv.tag)) Some(tv)
          else highest
        }
        case None => Some(tv)
      }

      if (received + 1 == quorum) {
        tagvalue match {
          case None => {
            respond_to ! None
            context.become(receive)
          }
          case Some(tv: TagValue) => {
            val children = picked_servers.map(s => context.actorOf(Props(new WriteSender(s, self, tv.tag.tagmax, key, tv.value))))
            children.foreach(_ ! Start)
            context.setReceiveTimeout(timeout.duration)
            context.become(waiting_for_replica(respond_to, 0, Some(GetResult(tv.value))))
          }
        }
      } else {
        context.become(waiting_for_get_responses(picked_servers, respond_to, received + 1, key, tagvalue))
      }
    }
    case None => {
      if (received + 1 == quorum) {
        val result = highest match {
          case None => None
          case Some(tv: TagValue) => Some(tv.value)
        }
        respond_to ! result
        context.become(receive)
      } else {
        context.become(waiting_for_get_responses(picked_servers, respond_to, received + 1, key, highest))
      }
    }
    case ReceiveTimeout => {
      log.warning("Timeout while waiting for get responses. Was: {}, Received: {}, Key: {}, Highest: {}", self, received, key, highest)
      respond_to ! Timedout
      context.become(receive)
    }
  }

  def waiting_for_acks(respond_to: ActorRef, received: Int, result: Ack): Receive = {
    case Ack => {
      if (received + 1 == quorum) {
        respond_to ! result
        context.become(receive)
      } else {
        context.become(waiting_for_acks(respond_to, received + 1, result))
      }
    }
    case ReceiveTimeout => {
      log.warning("Timeout while waiting for acks. Was: {}, Received: {}", self, received)
      respond_to ! Timedout
      context.become(receive)
    }
  }

  def waiting_for_put_responses(picked_servers: List[ActorRef], respond_to: ActorRef, received: Int, key: String, value: String, highest: Int = 0): Receive = {
    case Some(Tag(tm, _)) => {
      val tagmax = if (tm > highest) {
        tm
      } else {
        highest
      }

      if (received + 1 == quorum) {
        val children = picked_servers.map(s => context.actorOf(Props(new WriteSender(s, self, tagmax, key, value))))
        children.foreach(_ ! Start)
        context.setReceiveTimeout(timeout.duration)
        context.become(waiting_for_acks(respond_to, 0, Ack()))
      } else {
        context.become(waiting_for_put_responses(picked_servers, respond_to, received + 1, key, value, tagmax))
      }
    }
    case None => {
      if (received + 1 == quorum) {
        val children = picked_servers.map(s => context.actorOf(Props(new WriteSender(s, self, highest, key, value))))
        children.foreach(_ ! Start)
        context.setReceiveTimeout(timeout.duration)
        context.become(waiting_for_acks(respond_to, 0, Ack()))
      } else {
        context.become(waiting_for_put_responses(picked_servers, respond_to, received + 1, key, value, highest))
      }
    }
    case ReceiveTimeout => {
      log.warning("Timeout while waiting for put responses. Was: {}, Received: {}, Key: {}, Value: {}, Highest: " + highest, self, received, key, value)
      respond_to ! Timedout
      context.become(receive)
    }
  }

  def get(key: String) = {
    val picked_servers = pick_servers(key)

    val children = picked_servers.map(s => context.actorOf(Props(new ReadSender(s, self, key))))
    children.foreach(_ ! Start)
    context.setReceiveTimeout(timeout.duration)
    context.become(waiting_for_get_responses(picked_servers, sender, 0, key))
  }

  def put(key: String, value: String) = {
    val picked_servers = pick_servers(key)

    val children = picked_servers.map(s => context.actorOf(Props(new ReadTagSender(s, self, key))))
    children.foreach(_ ! Start)
    context.setReceiveTimeout(timeout.duration)
    context.become(waiting_for_put_responses(picked_servers, sender, 0, key, value))
  }

  def receive = {
    case Put(key, value) => put(key, value)
    case Get(key) => get(key)
  }
}
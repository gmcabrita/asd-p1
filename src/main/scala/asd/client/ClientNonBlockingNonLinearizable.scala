package asd

import asd.message._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ReceiveTimeout
import akka.actor.ActorDSL._
import akka.actor.Props
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ClientNonBlockingNonLinearizable(servers: List[ActorRef], quorum: Int, degree_of_replication: Int) extends ClientNonBlocking(servers, quorum, degree_of_replication) {
  override def waiting_for_get_responses(picked_servers: List[ActorRef], respond_to: ActorRef, received: Int, key: String, highest: Option[TagValue] = None): Receive = {
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
            respond_to ! Some(GetResult(tv.value))
            context.become(receive)
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
}
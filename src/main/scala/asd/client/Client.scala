package asd

import asd.message._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ActorDSL._

class Client(servers: List[ActorRef], quorum: Int, degree_of_replication: Int) extends Actor {
  implicit val system = ActorSystem("CLIENT")

  def pick_servers(key: String): List[ActorRef] = {
    val start = Math.abs(key.hashCode() % degree_of_replication)

    val picked = servers.slice(start, start + degree_of_replication)
    if (picked.size < degree_of_replication) {
      picked ++ servers.slice(0, degree_of_replication - picked.size)
    } else {
      picked
    }
  }

  def get(key: String) = {
    implicit val box = inbox()
    val picked_servers = pick_servers(key)

    // send reads to n servers
    picked_servers.foreach((s) => {
      box.send(s, Read(key))
    })

    // wait for quorum of answers
    val highest_tagvalue = (1 to quorum).fold(None)((acc, _) => {
      box.select() {
        case Some(tagvalue: TagValue) => {
          acc match {
            case None => Some(tagvalue)
            case Some(old_tagvalue: TagValue) => {
              if (tagvalue.tag.compareTo(old_tagvalue.tag)) Some(tagvalue)
              else acc
            }
          }
        }
        case None => {
          acc
        }
      }
    })

    highest_tagvalue match {
      case Some(tv: TagValue) => { // key exists, replicate to all picked servers before replying
        picked_servers.foreach((s) => {
          box.send(s, Write(tv.tag.tagmax, key, tv.value))
        })

        // wait for quorum of acks
        for (i <- 1 to quorum) {
          box.select() {
            case Ack => ()
          }
        }

        box.send(sender, Some(GetResult(tv.value)))
      }
      case None => box.send(sender, None) // key doesn't exist
    }
  }

  def put(key: String, value: String) = {
    implicit val box = inbox()
    val picked_servers = pick_servers(key)

    // send readtags to n servers
    picked_servers.foreach((s) => {
      box.send(s, ReadTag(key))
    })


    // wait for the quorum of answers
    val highest_tagmax = (1 to quorum).fold(-1)((highest_tagmax, _) => {
      val tagmax = box.select() {
        case Some(Tag(tm, _)) => tm
        case None => 0
      }

      if (tagmax > highest_tagmax) {
        tagmax
      } else {
        highest_tagmax
      }
    })

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

    box.send(sender, Ack)
  }

  def receive = {
    case Put(key, value) => put(key, value)
    case Get(key) => get(key)
  }
}
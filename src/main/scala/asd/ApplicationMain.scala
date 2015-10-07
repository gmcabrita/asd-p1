package asd

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorDSL._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
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

object KVStore extends App {
  implicit val system = ActorSystem("APP")
  implicit val timeout = Timeout(5 seconds)

  class Server extends Actor {
    var store = new ParHashMap[String, TagValue]

    def receive = {
      case Write(tagmax, key, value) => {
        store.get(key) match {
          case Some(TagValue(old_tag, _)) => {
            val tv = TagValue(Tag(tagmax, sender), value)
            if (compareTags(tv.tag, old_tag)) {
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

  class Client(servers: List[ActorRef], quorum: Int, degree_of_replication: Int) extends Actor {
    def pick_servers(key: String): List[ActorRef] = {
      val start = Math.abs(key.hashCode() % degree_of_replication)

      val picked = servers.slice(start, start + degree_of_replication)
      if (picked.size < degree_of_replication) {
        picked ++ servers.slice(0, degree_of_replication - picked.size)
      } else {
        picked
      }
    }

    def receive = {
      case Put(key, value) => {
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
      case Get(key) => {
        implicit val system = ActorSystem("ASD")
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
                  if (compareTags(tagvalue.tag, old_tagvalue.tag)) Some(tagvalue)
                  else acc
                }
              }
            }
            case None => acc
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
    }
  }

  // t1 > t2
  def compareTags(t1: Tag, t2: Tag): Boolean = {
    t1.tagmax > t2.tagmax || (t1.tagmax == t2.tagmax && t1.actor_ref.compareTo(t2.actor_ref) > 0)
  }

  def get(client: ActorRef, key: String) = {
    Await.result(client.ask(Get(key)), timeout.duration) match {
      case Some(GetResult(x)) => println(x)
      case None => println("Value does not exist.")
    }
  }

  def put(client: ActorRef, key: String, value: String): Boolean = {
    Await.result(client.ask(Put(key, value)), timeout.duration) match {
      case Ack => true
      case _ => false
    }
  }

  val servers = (1 to 30).toList.map(_ => system.actorOf(Props[Server]))
  val quorum = 3
  val degree_of_replication = 5

  val c1 = system.actorOf(Props(new Client(servers, quorum, degree_of_replication)))
  val c2 = system.actorOf(Props(new Client(servers, quorum, degree_of_replication)))

  get(c1, "a")

  println(put(c1, "a", "b"))
  println(put(c2, "a", "c"))

  get(c1, "a")
  get(c2, "a")
}
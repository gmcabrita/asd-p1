package asd

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ActorDSL._

class ClientNonLinearizable(servers: List[ActorRef], quorum: Int, degree_of_replication: Int) extends Client(servers, quorum, degree_of_replication) {
  override def get(key: String) = {
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
        case None => acc
      }
    })

    highest_tagvalue match {
      case Some(tv: TagValue) => box.send(sender, Some(GetResult(tv.value)))
      case None => box.send(sender, None)
    }
  }
}
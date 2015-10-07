package asd

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

case class Tag(tagmax: Int, actor_ref: ActorRef) {
  // this > that
  def compareTo(that: Tag): Boolean = {
    this.tagmax > that.tagmax || (this.tagmax == that.tagmax && this.actor_ref.compareTo(that.actor_ref) > 0)
  }
}
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
package asd.evaluation

import asd.rand.Zipf
import asd._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask

import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

import scala.concurrent.ExecutionContext.Implicits.global

import scala.reflect.ClassTag
import scala.reflect._

import akka.event.Logging
import akka.event.LoggingAdapter

class LocalNoFailureEvaluation(number_of_keys: Int, number_of_clients: Int, number_of_servers: Int, quorum: Int, degree_of_replication: Int, rw_ratio: (Int, Int), seed: Int, linearizable: Boolean) extends Actor {
  val zipf = new Zipf(number_of_keys, seed)
  val r = new Random(seed)
  implicit val system = ActorSystem("EVAL")
  implicit val timeout = Timeout(10 seconds)
  val log = Logging.getLogger(system, this)

  val servers: Vector[ActorRef] = (1 to number_of_servers).toVector.map(_ => system.actorOf(Props[Server]))
  val clients: Vector[ActorRef] = (1 to number_of_clients).toVector.map(_ => {
    if (linearizable) system.actorOf(Props(new ClientNonBlocking(servers.toList, quorum, degree_of_replication)))
    else system.actorOf(Props(new ClientNonBlockingNonLinearizable(servers.toList, quorum, degree_of_replication)))
  })

  var operations = 10000
  var reads: Long = 0
  var writes: Long = 0

  var begin: Long = 0
  var end: Long = 0

  def continue(actr: ActorRef) = {
     operations -= 1

    if (operations == 0) {
      end = System.nanoTime
      println("reads: " + reads)
      println("writes: " + writes)
      println("elapsed time: " + (end - begin)/1e6+"ms")
      sys.exit(0)
    }

    actr ! gen_op()
  }

  def gen_op() = {
    val float = r.nextFloat()
    val key = zipf.nextZipf().toString
    if (float > (rw_ratio._2 / 100f)) { // read
      reads += 1
      Get(key)
    } else { // write
      val value = r.nextString(16)
      writes += 1
      Put(key, value)
    }
  }

  def receive = {
    case Start => {
      begin = System.nanoTime
      operations -= number_of_clients
      //clients.foreach(_ ! gen_op())
      for (i <- 0 to number_of_clients - 1) {
        system.scheduler.scheduleOnce(5*i millis, clients(i), gen_op)
      }
    }
    case _ => continue(sender)
  }
}
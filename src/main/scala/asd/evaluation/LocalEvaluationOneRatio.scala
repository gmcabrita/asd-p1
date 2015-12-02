package asd.evaluation

import asd.message._
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

class LocalEvaluationOneRatio(num_keys: Int, num_servers: Int, num_clients: Int, num_replicas: Int, quorum: Int, linearizable: Boolean, run_time: Long, rw_ratio: (Int, Int), seed: Long, num_faults: Int) extends Actor {
  val zipf = new Zipf(num_keys, seed)
  val r = new Random(seed)
  implicit val system = ActorSystem("EVAL")
  implicit val timeout = Timeout(3 seconds)

  val servers: Vector[ActorRef] = (1 to num_servers).toVector.map(_ => system.actorOf(Props[Server]))
  val clients: Vector[ActorRef] = (1 to num_clients).toVector.map(_ => {
    if (linearizable) system.actorOf(Props(new ClientNonBlocking(servers.toList, quorum, num_replicas)))
    else system.actorOf(Props(new ClientNonBlockingNonLinearizable(servers.toList, quorum, num_replicas)))
  })

  // fault injection
  val fault_rand = new Random()
  fault_rand.shuffle(0 to num_servers - 1).take(num_faults).foreach(i => servers(i) ! Stop)

  var reads: Long = 0
  var writes: Long = 0

  var begin: Long = 0
  var end: Long = 0

  var run: Int = 0

  def continue(actr: ActorRef) = {
    val time = System.nanoTime

    if (time - begin >= run_time * 1e6) {
      end = time
      println("reads: " + reads)
      println("writes: " + writes)
      println("elapsed time: " + (end - begin)/1e6+"ms")

      Thread.sleep(5000)

      val results: Vector[Future[Any]] = clients.map(c => ask(c, Stop))
      val final_results = Future.fold[Any, (Double, Double, Double)](results)((0, Double.MinValue, Double.MaxValue))((acc, r) => {
        (acc, r) match {
          case ((avg, high, low), AvgLatency(v)) => {
            if (v > high && v < low) (avg + v, v, v)
            else if (v > high) (avg + v, v, low)
            else if (v < low) (avg + v, high, v)
            else (avg + v, high, low)
          }
        }
      })

      Await.result(final_results, 1 second).asInstanceOf[(Double, Double, Double)] match {
        case (avg, high, low) => {
          println("Average client latency: " + avg / results.length)
          println("Highest client latency: " + high)
          println("Lowest client latency: " + low)
        }
      }

      Thread.sleep(2000)

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
      clients.foreach(_ ! gen_op())
    }
    case _ => continue(sender)
  }
}
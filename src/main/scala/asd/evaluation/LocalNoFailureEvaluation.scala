package asd.evaluation

import asd.rand.Zipf
import asd.Client
import asd.ClientNonLinearizable
import asd.Server
import asd._

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask

import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class LocalNoFailureEvaluation(number_of_keys: Int, number_of_clients: Int, number_of_servers: Int, quorum: Int, degree_of_replication: Int, rw_ratio: (Int, Int), seed: Int) {
  val zipf = new Zipf(number_of_keys, seed)
  val r = new Random(seed)
  implicit val system = ActorSystem("EVAL")
  implicit val timeout = Timeout(10 seconds)

  def spawn_servers(): Vector[ActorRef] = (1 to number_of_servers).toVector.map(_ => system.actorOf(Props[Server]))
  def spawn_clients(servers: Vector[ActorRef]): Vector[ActorRef] = (1 to number_of_clients).toVector.map(_ => system.actorOf(Props(new Client(servers.toList, quorum, degree_of_replication))))

  def run() = {
    val servers = spawn_servers()
    val clients = spawn_clients(servers)

    var writes = 0
    var reads = 0

    val begin = System.nanoTime

    val stuff = (1 to 100000).par.map(_ => {
      val float = r.nextFloat()
      val client = clients(r.nextInt(clients.length))
      val key = zipf.nextZipf().toString
      if (float > (rw_ratio._2 / 100f)) { // read
        Await.result(client.ask(Get(key)), timeout.duration)
        reads += 1
      } else { // write
        val value = r.nextString(16)
        Await.result(client.ask(Put(key, value)), timeout.duration)
        writes += 1
      }
    })

    val end = System.nanoTime

    println("reads: " + reads)
    println("writes: " + writes)
    println("elapsed time: " + (end - begin)/1e6+"ms")

    clients.par.foreach(x => system.stop(x))
    servers.par.foreach(x => system.stop(x))
    // how to distribute the read/writes over the 1000 keys?
    // idea is to send away all the requests and then Await for all of them at the end and then halt the execution
  }

  // client.ask(Get(key))
  // client.ask(Put(key, value))
  // put every future into `everything`
  // everything.reduce(x => Await.result(x, timeout.duration)) before exiting
}

// zipf
// 1000 keys
// read/write ratios: 90/10, 50/50, 10/90
// spawn 1 / 4 / 8 / 12 clients
// 12 servers in one machine

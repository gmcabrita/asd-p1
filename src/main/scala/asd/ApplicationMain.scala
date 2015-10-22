package asd

import asd.evaluation.LocalEvaluation
import asd.evaluation.DistributedEvaluation
import asd._

import akka.actor.{ActorRef, Props, ActorSystem}

import com.typesafe.config.ConfigFactory
import java.io.File

import asd.message._

object KVStore extends App {
  implicit val system = ActorSystem("MAIN")

  // val eval = system.actorOf(Props(new LocalEvaluation(
  //   1000, // num keys
  //   12, // num clients
  //   12, // num servers
  //   7, // quorum
  //   12, // degree of replication
  //   192371441, // seed
  //   true, // linearizable?
  //   10000, // number of operations
  //   5, // number of injected faults
  //   5 // runs per case
  // )))

  val eval = system.actorOf(Props(new DistributedEvaluation(
    1000, // num keys
    12, // num clients
    12, // num servers
    7, // quorum
    12, // degree of replication
    192371441, // seed
    true, // linearizable?
    1000, // number of operations
    0 // number of injected faults
  )))

  eval ! Start
}
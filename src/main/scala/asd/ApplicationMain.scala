package asd

import asd.evaluation.LocalEvaluation
import asd.evaluation.LocalEvaluationOneRatio
import asd.evaluation.DistributedEvaluation
import asd.evaluation.DistributedEvaluationOneRatio
import asd._

import akka.actor.{ActorRef, Props, ActorSystem, AddressFromURIString, Deploy}
import akka.remote.RemoteScope


import com.typesafe.config.ConfigFactory
import java.io.File

import asd.message._

object KVStore extends App {
  val config = ConfigFactory.parseFile(new File("src/main/resources/deploy.conf")).resolve()
  implicit val system = ActorSystem("DeployerSystem", config)

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
  //   1 // runs per case
  // )))

  val d = AddressFromURIString(config.getString("deployer.path"))

  val eval = system.actorOf(Props(new DistributedEvaluationOneRatio(
    1000, // num keys
    12, // num servers
    1, // num clients
    3, // num replicas
    2, // quorum
    false, // linearizable?
    10000, // run time in milliseconds
    (90, 10), // rw ratio
    192371441, // seed
    0, // number of injected faults
    system
  )).withDeploy(Deploy(scope = RemoteScope(d))), "deployer")

  // val eval = system.actorOf(Props(new DistributedEvaluation(
  //   1000, // num keys
  //   12, // num clients
  //   12, // num servers
  //   7, // quorum
  //   12, // degree of replication
  //   192371441, // seed
  //   true, // linearizable?
  //   1000, // number of operations
  //   0 // number of injected faults
  // )))

  eval ! Start
}

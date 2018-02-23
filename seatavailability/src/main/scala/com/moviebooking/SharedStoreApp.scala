package com.moviebooking

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.moviebooking.aggregates.{Command, Screen}
import com.moviebooking.common.ClusterSettings

import scala.concurrent.duration._

object SharedStoreApp extends App {
  private val settings = new ClusterSettings(2552)
  private val sharedJournalPath = ActorPath.fromString(settings.sharedStorePath)
  val system = settings.system


  val numberOfShards = 100

  ClusterSharding(system).start(
    typeName = "Screen",
    entityProps = Props[Screen],
    settings = ClusterShardingSettings(system),
    extractEntityId = Command.idExtractor,
    extractShardId = Command.shardResolver)

  startupSharedJournal(system, startStore = true)


  def startupSharedJournal(system: ActorSystem, startStore: Boolean): Unit = {
    // Start the shared journal one one node (don't crash this SPOF)
    // This will not be needed with a distributed journal

    val store = system.actorOf(Props[SharedLeveldbStore], "store")
    registerSharedJournal(system)
  }

  def registerSharedJournal(system: ActorSystem) = {
    val settings = new ClusterSettings(2552)
    val sharedJournalPath = ActorPath.fromString(settings.sharedStorePath)
    // register the shared journal
    import system.dispatcher
    implicit val timeout = Timeout(15.seconds)
    println(s"Trying to find ${sharedJournalPath}")
    val f = (system.actorSelection(sharedJournalPath) ? Identify(None))
    f.onSuccess {
      case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
      case _ =>
        system.log.error("Shared journal not started at {}", sharedJournalPath)
        system.terminate()
    }
    f.onFailure {
      case _ =>
        system.log.error("Lookup of shared journal at {} timed out", sharedJournalPath)
        system.terminate()
    }
  }
}


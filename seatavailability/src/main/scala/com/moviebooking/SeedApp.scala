package com.moviebooking

import akka.actor.Props
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.moviebooking.aggregates.{Command, Screen}
import com.moviebooking.common.ClusterSettings

object SeedApp extends App {
  private val settings = new ClusterSettings(2552)
  val system = settings.system


  ClusterSharding(system).start(
    typeName = "Screen",
    entityProps = Props[Screen],
    settings = ClusterShardingSettings(system),
    extractEntityId = Command.idExtractor,
    extractShardId = Command.shardResolver)

  SharedStoreApp.registerSharedJournal(system)
}

package idesyde.common.legacy

import scala.jdk.CollectionConverters._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import scala.collection.mutable
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths
import idesyde.common.legacy.CommonModule.tryCast

trait PlatformRules {

  def identSchedulableTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SchedulableTiledMultiCore], Set[String]) = {
    tryCast(identified, classOf[PartitionedCoresWithRuntimes]) { runtimes =>
      tryCast(identified, classOf[TiledMultiCoreWithFunctions]) { plats =>
        (
          runtimes.flatMap(r =>
            plats.map(p => SchedulableTiledMultiCore(hardware = p, runtimes = r))
          ),
          Set()
        )
      }
    }
  }

  def identPartitionedSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PartitionedSharedMemoryMultiCore], Set[String]) = {
    tryCast(identified, classOf[PartitionedCoresWithRuntimes]) { runtimes =>
      tryCast(identified, classOf[SharedMemoryMultiCore]) { plats =>
        (
          runtimes.flatMap(r =>
            plats.map(p => PartitionedSharedMemoryMultiCore(hardware = p, runtimes = r))
          ),
          Set()
        )
      }
    }
  }

  def identTiledFromShared(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[TiledMultiCoreWithFunctions], Set[String]) = {
    tryCast(identified, classOf[SharedMemoryMultiCore]) { plats =>
      var tiledPlats = mutable.Set[TiledMultiCoreWithFunctions]()
      var errors     = mutable.Set[String]()
      for (plat <- plats) {
        val isTiled = plat.communicationElems.forall(p =>
          plat.topology
            .outgoingEdgesOf(p)
            .asScala
            .map(plat.topology.getEdgeTarget)
            .count(e => plat.storageElems.contains(e) || plat.processingElems.contains(e)) <= 2
        ) &&
          plat.storageElems.forall(p =>
            plat.topology
              .outgoingEdgesOf(p)
              .asScala
              .map(plat.topology.getEdgeTarget)
              .count(e => plat.communicationElems.contains(e)) <= 1
          ) &&
          plat.processingElems.length == plat.storageElems.length
        if (isTiled) {
          val shortestPaths = FloydWarshallShortestPaths(plat.topology)
          val tiledMemories = plat.processingElems.map(pe =>
            plat.storageElems.minBy(me =>
              // plat.topology.get(pe).shortestPathTo(plat.topology.get(me)) match {
              //   case Some(path) => path.size
              //   case None       => plat.communicationElems.length + 1
              // }
              val path = shortestPaths.getPath(pe, me)
              if (path != null) {
                path.getLength()
              } else {
                plat.communicationElems.length + 1
              }
            )
          )
          val tiledNI = plat.processingElems.map(pe =>
            plat.communicationElems.minBy(ce =>
              // plat.topology.get(pe).shortestPathTo(plat.topology.get(ce)) match {
              //   case Some(value) => value.size
              //   case None        => plat.topology.nodes.size
              // }
              val path = shortestPaths.getPath(pe, ce)
              if (path != null) {
                path.getLength()
              } else {
                plat.communicationElems.length + 1
              }
            )
          )
          val routers = plat.communicationElems.filterNot(tiledNI.contains)
          tiledPlats += TiledMultiCoreWithFunctions(
            processors = plat.processingElems,
            memories = tiledMemories,
            networkInterfaces = tiledNI,
            routers = routers,
            interconnectTopologySrcs = plat.topologySrcs,
            interconnectTopologyDsts = plat.topologyDsts,
            processorsProvisions = plat.processorsProvisions,
            processorsFrequency = plat.processorsFrequency,
            tileMemorySizes =
              tiledMemories.map(me => plat.storageSizes(plat.storageElems.indexOf(me))),
            communicationElementsMaxChannels = plat.communicationElementsMaxChannels,
            communicationElementsBitPerSecPerChannel =
              plat.communicationElementsBitPerSecPerChannel,
            preComputedPaths = plat.preComputedPaths
          )
        } else {
          errors += s"identTiledFromShared: The shared memory platform containing processing element ${plat.processingElems.head} is not tiled."
        }
      }
      (tiledPlats.toSet, errors.toSet)
    }
  }

}

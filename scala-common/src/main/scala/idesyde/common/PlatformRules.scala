package idesyde.common

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import scala.collection.mutable

trait PlatformRules {

  def identSchedulableTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SchedulableTiledMultiCore] = {
    val runtimes = identified
      .filter(_.isInstanceOf[PartitionedCoresWithRuntimes])
      .map(_.asInstanceOf[PartitionedCoresWithRuntimes])
    val plat = identified.filter(_.isInstanceOf[TiledMultiCore]).map(_.asInstanceOf[TiledMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    runtimes.flatMap(r => plat.map(p => SchedulableTiledMultiCore(hardware = p, runtimes = r)))
  }

  def identPartitionedSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[PartitionedSharedMemoryMultiCore] = {
    val runtimes = identified
      .filter(_.isInstanceOf[PartitionedCoresWithRuntimes])
      .map(_.asInstanceOf[PartitionedCoresWithRuntimes])
    val plat = identified
      .filter(_.isInstanceOf[SharedMemoryMultiCore])
      .map(_.asInstanceOf[SharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    runtimes.flatMap(r =>
      plat.map(p => PartitionedSharedMemoryMultiCore(hardware = p, runtimes = r))
    )
  }

  def identTiledFromShared(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[TiledMultiCore] = {
    val plats = identified
      .filter(_.isInstanceOf[SharedMemoryMultiCore])
      .map(_.asInstanceOf[SharedMemoryMultiCore])
    var tiledPlats = mutable.Set[TiledMultiCore]()
    for (plat <- plats) {
      val isTiled = plat.communicationElems.forall(p =>
        plat.topology
          .get(p)
          .neighbors
          .map(_.value)
          .count(e => plat.storageElems.contains(e) || plat.processingElems.contains(e)) <= 2
      ) &&
        plat.storageElems.forall(p =>
          plat.topology
            .get(p)
            .neighbors
            .map(_.value)
            .count(e => plat.communicationElems.contains(e)) <= 1
        ) &&
        plat.processingElems.length == plat.storageElems.length
      if (isTiled) {
        val tiledMemories = plat.processingElems.map(pe =>
          plat.storageElems.minBy(me =>
            plat.topology.get(pe).shortestPathTo(plat.topology.get(me)).size
          )
        )
        val tiledNI = plat.processingElems.map(pe =>
          plat.communicationElems.minBy(ce =>
            plat.topology.get(pe).shortestPathTo(plat.topology.get(ce)) match {
              case Some(value) => value.size
              case None        => plat.topology.nodes.size
            }
          )
        )
        val routers = plat.communicationElems.filterNot(tiledNI.contains)
        tiledPlats += TiledMultiCore(
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
          communicationElementsBitPerSecPerChannel = plat.communicationElementsBitPerSecPerChannel,
          preComputedPaths = plat.preComputedPaths
        )
      }
    }
    tiledPlats.toSet
  }

}

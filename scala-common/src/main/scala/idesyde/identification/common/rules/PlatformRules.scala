package idesyde.identification.common.rules

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.platform.PartitionedCoresWithRuntimes
import idesyde.identification.common.models.platform.TiledMultiCore
import idesyde.identification.common.models.platform.SharedMemoryMultiCore
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore

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

}

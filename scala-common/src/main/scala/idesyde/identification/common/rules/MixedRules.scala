package idesyde.identification.common.rules

import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.mixed.SDFToPartitionedSharedMemory

object MixedRules {

  def identSDFToTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Option[SDFToTiledMultiCore] = {
    val app = identified
      .find(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    val plat = identified
      .find(_.isInstanceOf[SchedulableTiledMultiCore])
      .map(_.asInstanceOf[SchedulableTiledMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        SDFToTiledMultiCore(
          sdfApplications = a,
          platform = p,
          processMappings = Array.empty,
          memoryMappings = Array.empty,
          messageSlotAllocations = Array.empty
        )
      )
    )
  }

  def identSDFToPartitionedSharedMemory(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Option[SDFToPartitionedSharedMemory] = {
    val app = identified
      .find(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    val plat = identified
      .find(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        SDFToPartitionedSharedMemory(
          sdfApplications = a,
          platform = p,
          processMappings = Array.empty,
          memoryMappings = Array.empty,
          messageSlotAllocations = Array.empty
        )
      )
    )
  }

}

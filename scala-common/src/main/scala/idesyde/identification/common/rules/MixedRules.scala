package idesyde.identification.common.rules

import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.mixed.SDFToPartitionedSharedMemory
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import spire.math.Rational

object MixedRules {

  def identSDFToTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SDFToTiledMultiCore] = {
    val app = identified
      .filter(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    val plat = identified
      .filter(_.isInstanceOf[SchedulableTiledMultiCore])
      .map(_.asInstanceOf[SchedulableTiledMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        SDFToTiledMultiCore(
          sdfApplications = a,
          platform = p,
          processMappings = Vector.empty,
          messageMappings = Vector.empty,
          schedulerSchedules = Vector.empty,
          messageSlotAllocations = Vector.empty
        )
      )
    )
  }

  def identSDFToPartitionedSharedMemory(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SDFToPartitionedSharedMemory] = {
    val app = identified
      .filter(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        SDFToPartitionedSharedMemory(
          sdfApplications = a,
          platform = p,
          processMappings = Vector.empty,
          memoryMappings = Vector.empty,
          messageSlotAllocations = Vector.empty
        )
      )
    )
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[PeriodicWorkloadToPartitionedSharedMultiCore] = {
    val app = identified
      .filter(_.isInstanceOf[CommunicatingExtendedDependenciesPeriodicWorkload])
      .map(_.asInstanceOf[CommunicatingExtendedDependenciesPeriodicWorkload])
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        PeriodicWorkloadToPartitionedSharedMultiCore(
          workload = a,
          platform = p,
          processMappings = Vector.empty,
          processSchedulings = Vector.empty,
          channelMappings = Vector.empty,
          channelSlotAllocations = Map(),
          maxUtilizations = Map()
        )
      )
    )
  }

}

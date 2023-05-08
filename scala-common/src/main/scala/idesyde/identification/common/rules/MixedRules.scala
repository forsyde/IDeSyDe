package idesyde.identification.common.rules

import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.identification.common.models.mixed.{
  PeriodicWorkloadToPartitionedSharedMultiCore,
  SDFToPartitionedSharedMemory,
  SDFToTiledMultiCore,
  TaskdAndSDFServer,
  TasksAndSDFServerToMultiCore
}
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import spire.math.Rational
import idesyde.utils.Logger
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload

trait MixedRules(using logger: Logger) {

  def identTaskdAndSDFServer(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[TaskdAndSDFServer] = {
    val sdfDecisionModel = identified
      .filter(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
    if (sdfDecisionModel.exists(_.isConsistent))
      logger.debug("At least one SDF decision model is inconsistent.")
    val taskDecisionModel = identified
      .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
    sdfDecisionModel
      .filter(_.isConsistent)
      .flatMap(a =>
        taskDecisionModel.map(b =>
          TaskdAndSDFServer(
            sdfApplications = a,
            workload = b,
            sdfServerPeriod = Vector.empty,
            sdfServerBudget = Vector.empty
          )
        )
      )

  }

  def identSDFToTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SDFToTiledMultiCore] = {
    val app = identified
      .filter(_.isInstanceOf[SDFApplication])
      .map(_.asInstanceOf[SDFApplication])
      .filter(_.isConsistent) // only go forward if the SDF is consistent
    if (app.isEmpty)
      logger.debug("SDFApplication is not consistent. Impossible to identify SDFToTiledMultiCore.")
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
          messageSlotAllocations = Vector.empty,
          actorThroughputs = Vector.empty
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
      .filter(_.isConsistent) // only go forward if the SDF is consistent
    if (app.isEmpty)
      logger.debug(
        "SDFApplication is not consistent. Impossible to identify SDFToPartitionedSharedMemory."
      )
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
      .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
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

  def identTaksAndSDFServerToMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[TasksAndSDFServerToMultiCore] = {
    val app = identified
      .filter(_.isInstanceOf[TaskdAndSDFServer])
      .map(_.asInstanceOf[TaskdAndSDFServer])
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        TasksAndSDFServerToMultiCore(
          tasksAndSDFs = a,
          platform = p,
          processesMappings = Vector.empty,
          messagesMappings = Vector.empty,
          messageSlotAllocations = Map.empty
        )
      )
    )
  }

}

package idesyde.common

import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.utils.Logger

trait MixedRules {

  def identTaskdAndSDFServer(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[PeriodicWorkloadAndSDFServers] = {
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
          PeriodicWorkloadAndSDFServers(
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
  )(using logger: Logger): Set[SDFToTiledMultiCore] = {
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
  )(using logger: Logger): Set[SDFToPartitionedSharedMemory] = {
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
      plat.flatMap(p =>
        val potential = PeriodicWorkloadToPartitionedSharedMultiCore(
          workload = a,
          platform = p,
          processMappings = Vector.empty,
          processSchedulings = Vector.empty,
          channelMappings = Vector.empty,
          channelSlotAllocations = Map(),
          maxUtilizations = Map()
        )
        if (potential.wcets.forall(_.exists(_ > 0.0))) {
          Some(potential)
        } else None
      )
    )
  }

  def identTaksAndSDFServerToMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[PeriodicWorkloadAndSDFServerToMultiCore] = {
    val app = identified
      .filter(_.isInstanceOf[PeriodicWorkloadAndSDFServers])
      .map(_.asInstanceOf[PeriodicWorkloadAndSDFServers])
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    app.flatMap(a =>
      plat.map(p =>
        PeriodicWorkloadAndSDFServerToMultiCore(
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
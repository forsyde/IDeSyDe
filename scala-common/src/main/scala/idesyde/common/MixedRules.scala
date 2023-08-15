package idesyde.common

import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.utils.Logger
import scala.collection.mutable

trait MixedRules {

  def identTaskdAndSDFServer(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadAndSDFServers], Set[String]) = {
    var errors = mutable.Set[String]()
    val sdfDecisionModel = identified
      .filter(_.isInstanceOf[SDFApplicationWithFunctions])
      .map(_.asInstanceOf[SDFApplicationWithFunctions])
    for (a <- sdfDecisionModel) {
      if (!a.isConsistent) {
        errors += s"identTaskdAndSDFServer: SDFApplication containing ${a.actorsIdentifiers.head} is inconsistent. Ignoring it."
      }
    }
    val taskDecisionModel = identified
      .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
    (
      sdfDecisionModel
        .filter(_.isConsistent)
        .flatMap(a =>
          taskDecisionModel.map(b =>
            PeriodicWorkloadAndSDFServers(
              sdfApplications = a,
              workload = b
            )
          )
        ),
      errors.toSet
    )

  }

  def identSDFToTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SDFToTiledMultiCore], Set[String]) = {
    var errors = mutable.Set[String]()
    val app = identified
      .filter(_.isInstanceOf[SDFApplicationWithFunctions])
      .map(_.asInstanceOf[SDFApplicationWithFunctions])
    for (a <- app) {
      if (!a.isConsistent) {
        errors += s"identSDFToTiledMultiCore: SDFApplication containing ${a.actorsIdentifiers.head} is inconsistent. Ignoring it."
      }
    }
    val plat = identified
      .filter(_.isInstanceOf[SchedulableTiledMultiCore])
      .map(_.asInstanceOf[SchedulableTiledMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    (
      app
        .filter(_.isConsistent)
        .flatMap(a =>
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
        ),
      errors.toSet
    )
  }

  def identSDFToPartitionedSharedMemory(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SDFToPartitionedSharedMemory], Set[String]) = {
    var errors = mutable.Set[String]()
    val app = identified
      .filter(_.isInstanceOf[SDFApplicationWithFunctions])
      .map(_.asInstanceOf[SDFApplicationWithFunctions]) // only go forward if the SDF is consistent
    for (a <- app) {
      if (!a.isConsistent) {
        errors += s"identSDFToPartitionedSharedMemory: SDFApplication containing ${a.actorsIdentifiers.head} is inconsistent. Ignoring it."
      }
    }
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    (
      app
        .filter(_.isConsistent)
        .flatMap(a =>
          plat.map(p =>
            SDFToPartitionedSharedMemory(
              sdfApplications = a,
              platform = p,
              processMappings = Vector.empty,
              memoryMappings = Vector.empty,
              messageSlotAllocations = Vector.empty
            )
          )
        ),
      errors.toSet
    )
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadToPartitionedSharedMultiCore], Set[String]) = {
    val app = identified
      .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    val (m, e) = app
      .flatMap(a =>
        plat
          .map(p =>
            val potential = PeriodicWorkloadToPartitionedSharedMultiCore(
              workload = a,
              platform = p,
              processMappings = Vector.empty,
              processSchedulings = Vector.empty,
              channelMappings = Vector.empty,
              channelSlotAllocations = Map(),
              maxUtilizations = Map()
            )
            if (
              potential.wcets.zipWithIndex
                .forall((wi, i) => wi.exists(w => w > 0.0 && w <= a.relativeDeadlines(i)))
            ) {
              (Some(potential), None)
            } else {
              (
                None,
                Some(
                  "identPeriodicWorkloadToPartitionedSharedMultiCore: not all tasks are mappable to the platform"
                )
              )
            }
          )
      )
      .unzip
    (m.flatten, e.flatten)
  }

  def identTaksAndSDFServerToMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadAndSDFServerToMultiCore], Set[String]) = {
    val app = identified
      .filter(_.isInstanceOf[PeriodicWorkloadAndSDFServers])
      .map(_.asInstanceOf[PeriodicWorkloadAndSDFServers])
    val plat = identified
      .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
      .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
    (
      app.flatMap(a =>
        plat.map(p =>
          PeriodicWorkloadAndSDFServerToMultiCore(
            tasksAndSDFs = a,
            platform = p,
            processesSchedulings = Vector.empty,
            processesMappings = Vector.empty,
            messagesMappings = Vector.empty,
            messageSlotAllocations = Map.empty,
            sdfServerPeriod = Vector.empty[Double],
            sdfServerBudget = Vector.empty[Double],
            sdfOrderBasedSchedules = p.runtimes.schedulers.map(p => Vector.empty)
          )
        )
      ),
      Set()
    )
  }

}

package idesyde.common.legacy

import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import scala.collection.mutable
import idesyde.common.legacy.CommonModule.tryCast

trait MixedRules {

  def identTaskdAndSDFServer(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadAndSDFServers], Set[String]) = {
    var errors = mutable.Set[String]()
    tryCast(identified, classOf[SDFApplicationWithFunctions]) { sdfDecisionModels =>
      for (a <- sdfDecisionModels) {
        if (!a.isConsistent) {
          errors += s"identTaskdAndSDFServer: SDFApplication containing ${a.actorsIdentifiers.head} is inconsistent. Ignoring it."
        }
      }
      tryCast(identified, classOf[CommunicatingAndTriggeredReactiveWorkload]) { taskDecisionModels =>
        (
          sdfDecisionModels
            .filter(_.isConsistent)
            .flatMap(a =>
              taskDecisionModels.map(b =>
                PeriodicWorkloadAndSDFServers(
                  sdfApplications = a,
                  workload = b
                )
              )
            ),
          errors.toSet
        )
      }
    }
  }

  def identSDFToTiledMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SDFToTiledMultiCore], Set[String]) = {
    var errors = mutable.Set[String]()
    tryCast(identified, classOf[SDFApplicationWithFunctions]) { apps =>
      if (apps.isEmpty) {
        errors += "identSDFToTiledMultiCore: no SDFApplicationWithFunctions found"
      }
      for (a <- apps) {
        if (!a.isConsistent) {
          errors += s"identSDFToTiledMultiCore: SDFApplication containing ${a.actorsIdentifiers.head} is inconsistent. Ignoring it."
        }
      }
      tryCast(identified, classOf[SchedulableTiledMultiCore]) { plats =>
        if (plats.isEmpty) {
          errors += "identSDFToTiledMultiCore: no SchedulableTiledMultiCore found"
        }
        (
          apps
            .filter(_.isConsistent)
            .flatMap(a =>
              plats.map(p =>
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
    }
  }

  def identSDFToPartitionedSharedMemory(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[SDFToPartitionedSharedMemory], Set[String]) = {
    var errors = mutable.Set[String]()
    tryCast(identified, classOf[SDFApplicationWithFunctions]) { apps =>
      if (apps.isEmpty) {
        errors += "identSDFToPartitionedSharedMemory: no SDFApplicationWithFunctions found"
      }
      for (a <- apps) {
        if (!a.isConsistent) {
          errors += s"identSDFToPartitionedSharedMemory: SDFApplication containing ${a.actorsIdentifiers.head} is inconsistent. Ignoring it."
        }
      }
      tryCast(identified, classOf[PartitionedSharedMemoryMultiCore]) { plats =>
        if (plats.isEmpty) {
          errors += "identSDFToPartitionedSharedMemory: no PartitionedSharedMemoryMultiCore found"
        }
        (
          apps
            .filter(_.isConsistent)
            .flatMap(a =>
              plats.map(p =>
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
    }
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadToPartitionedSharedMultiCore], Set[String]) = {
    tryCast(identified, classOf[CommunicatingAndTriggeredReactiveWorkload]) { apps => 
      tryCast(identified, classOf[PartitionedSharedMemoryMultiCore]) { plats =>
        val (m, e) = apps
          .flatMap(a =>
            plats
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
                    .forall((wi, i) => wi.exists(w => w > 0.0 && w <= a.relative_deadlines(i)))
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
    }
    // val app = identified
    //   .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
    //   .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
    // val plat = identified
    //   .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
    //   .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
    // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
  }

  def identTaksAndSDFServerToMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadAndSDFServerToMultiCoreOld], Set[String]) = {
    tryCast(identified, classOf[PeriodicWorkloadAndSDFServers]) {apps => 
      tryCast(identified, classOf[PartitionedSharedMemoryMultiCore]) {plats => 
        (
          apps.flatMap(a =>
            plats.map(p =>
              PeriodicWorkloadAndSDFServerToMultiCoreOld(
                tasksAndSDFs = a,
                platform = p,
                processesSchedulings = Vector.empty,
                processesMappings = Vector.empty,
                messagesMappings = Vector.empty,
                messageSlotAllocations = Map.empty,
                sdfServerUtilization = Vector.empty[Double],
                sdfOrderBasedSchedules = p.runtimes.schedulers.map(p => Vector.empty)
              )
            )
          ),
          Set()
        )
      }
    }
  }

}

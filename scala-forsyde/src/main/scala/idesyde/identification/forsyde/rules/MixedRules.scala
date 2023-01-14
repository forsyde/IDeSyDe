package idesyde.identification.forsyde.rules

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.decision.results.AnalysedGenericProcessingModule
import forsyde.io.java.typed.viewers.decision.Scheduled
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule

object MixedRules {

  def integratePeriodicWorkloadToPartitionedSharedMultiCore(
      designModel: DesignModel,
      decisionModel: DecisionModel
  ): Option[? <: DesignModel] = {
    designModel match {
      case ForSyDeDesignModel(forSyDeSystemGraph) => {
        decisionModel match {
          case dse: PeriodicWorkloadToPartitionedSharedMultiCore => {
            val rebuilt = ForSyDeSystemGraph().merge(forSyDeSystemGraph)
            // dse.workload.tasks.zipWithIndex.foreach((t, i) => {
            //   rebuilt.addVertex(t.getViewedVertex)
            //   val analysed         = AnalysedTask.enforce(t)
            //   val responseTimeFrac = Rational(responseTimes(i).getValue(), timeMultiplier)
            //   val blockingTimeFrac = Rational(blockingTimes(i).getValue(), timeMultiplier)
            //   // scribe.debug(s"task ${t.getIdentifier} RT: (raw ${responseTimes(i).getValue}) ${responseTimeFrac.doubleValue}")
            //   // scribe.debug(s"task ${t.getIdentifier} BT: (raw ${blockingTimes(i).getValue}) ${blockingTimeFrac.doubleValue}")
            //   analysed.setWorstCaseResponseTimeNumeratorInSecs(responseTimeFrac.numerator.toLong)
            //   analysed.setWorstCaseResponseTimeDenominatorInSecs(
            //     responseTimeFrac.denominator.toLong
            //   )
            //   analysed.setWorstCaseBlockingTimeNumeratorInSecs(blockingTimeFrac.numerator.toLong)
            //   analysed.setWorstCaseBlockingTimeDenominatorInSecs(
            //     blockingTimeFrac.denominator.toLong
            //   )

            // })
            // dse.workload.dataBlocks.foreach(t => rebuilt.addVertex(t.getViewedVertex))
            // dse.schedHwModel.allocatedSchedulers.foreach(s => rebuilt.addVertex(s.getViewedVertex))
            // dse.schedHwModel.hardware.storageElems.foreach(m =>
            //   rebuilt.addVertex(m.getViewedVertex)
            // )
            // dse.platform.hardware.processingElems.zipWithIndex.foreach((m, j) =>
            //   val pe     = rebuilt.queryVertex(m).get()
            //   val module = AnalysedGenericProcessingModule.enforce(pe)
            //   module.setUtilization(
            //     active4StageDurationModule.durations.zipWithIndex
            //       .filter((ws, i) => taskExecution(i).isInstantiatedTo(j))
            //       .map((w, i) =>
            //         //scribe.debug(s"task n ${i} Wcet: (raw ${durations(i)(j)})")
            //         (Rational(w(j).getValue)
            //           / (periods(i))).toDouble
            //       )
            //       .sum
            //   )
            // )
            for (
              (taskId, schedId) <- dse.processSchedulings;
              task  = rebuilt.queryVertex(taskId).get();
              sched = rebuilt.queryVertex(schedId).get()
            ) {
              AbstractScheduler
                .safeCast(sched)
                .ifPresent(scheduler => {
                  Scheduled.enforce(task).insertSchedulersPort(rebuilt, scheduler)
                  GreyBox.enforce(sched).insertContainedPort(rebuilt, Visualizable.enforce(task))
                })
            }
            for (
              (taskId, memId) <- dse.processMappings;
              task = rebuilt.queryVertex(taskId).get();
              mem  = rebuilt.queryVertex(memId).get()
            ) {
              GenericMemoryModule
                .safeCast(mem)
                .ifPresent(memory =>
                  MemoryMapped.enforce(task).insertMappingHostsPort(rebuilt, memory)
                )
            }
            for (
              (channelId, memId) <- dse.channelMappings;
              channel = rebuilt.queryVertex(channelId).get();
              mem     = rebuilt.queryVertex(memId).get()
            ) {
              GenericMemoryModule
                .safeCast(mem)
                .ifPresent(memory =>
                  MemoryMapped.enforce(channel).insertMappingHostsPort(rebuilt, memory)
                )
            }
            Some(ForSyDeDesignModel(rebuilt))
          }
          case _ => Option.empty
        }
      }
      case _ => Option.empty
    }
  }

  def integrateSDFToTiledMultiCore(
      designModel: DesignModel,
      decisionModel: DecisionModel
  ): Option[? <: DesignModel] = {
    designModel match {
      case ForSyDeDesignModel(forSyDeSystemGraph) => {
        val newModel = ForSyDeSystemGraph().merge(forSyDeSystemGraph)
        decisionModel match {
          case dse: SDFToTiledMultiCore => {
            // first, we take care of the process mappings
            for (
              (mem, i) <- dse.processMappings.zipWithIndex;
              actorId   = dse.sdfApplications.actorsIdentifiers(i);
              memIdx    = dse.platform.hardware.memories.indexOf(mem);
              proc      = dse.platform.hardware.processors(memIdx);
              scheduler = dse.platform.runtimes.schedulers(memIdx)
            ) {
              newModel
                .queryVertex(actorId)
                .ifPresent(actor => {
                  newModel
                    .queryVertex(mem)
                    .ifPresent(m => {
                      val v = MemoryMapped.enforce(actor)
                      v.setMappingHostsPort(
                        newModel,
                        java.util.Set.of(GenericMemoryModule.enforce(m))
                      )
                    })
                  newModel
                    .queryVertex(scheduler)
                    .ifPresent(s => {
                      val v = Scheduled.enforce(actor)
                      v.setSchedulersPort(newModel, java.util.Set.of(AbstractScheduler.enforce(s)))
                    })
                })
            }
            Some(ForSyDeDesignModel(newModel))
          }
          case _ => Option.empty
        }
      }
      case _ => Option.empty
    }
  }
}

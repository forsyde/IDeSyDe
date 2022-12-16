package idesyde.identification.forsyde.rules

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.decision.results.AnalysedGenericProcessingModule
import forsyde.io.java.typed.viewers.decision.Scheduled
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler

object MixedRules {

  def integratePeriodicWorkloadToPartitionedSharedMultiCore(
      designModel: DesignModel,
      decisionModel: DecisionModel
  ): Option[? <: DesignModel] = {
    designModel match {
      case ForSyDeDesignModel(forSyDeSystemGraph) => {
        designModel match {
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
            taskMapping.zipWithIndex.foreach((mapping, i) => {
              val j      = mapping.getValue() // output.getIntVal(mapping)
              val task   = dse.workload.tasks(i)
              val memory = dse.schedHwModel.hardware.storageElems(j)
              MemoryMapped.enforce(task).insertMappingHostsPort(rebuilt, memory)
              // rebuilt.connect(task, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
              // GreyBox.enforce(memory)
              // rebuilt.connect(memory, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
            })
            dataBlockMapping.zipWithIndex.foreach((mapping, i) => {
              val j       = mapping.getValue() // output.getIntVal(mapping)
              val channel = dse.workload.dataBlocks(i)
              val memory  = dse.schedHwModel.hardware.storageElems(j)
              MemoryMapped.enforce(channel).insertMappingHostsPort(rebuilt, memory)
              // rebuilt.connect(channel, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
              GreyBox.enforce(memory).insertContainedPort(rebuilt, Visualizable.enforce(channel))
              // rebuilt.connect(memory, channel, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
            })
            Some(rebuilt)
          }
          case _ => Option.empty
        }
      }
      case _ => Option.empty
    }
  }
}

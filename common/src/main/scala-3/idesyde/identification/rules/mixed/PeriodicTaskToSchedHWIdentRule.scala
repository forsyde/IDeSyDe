package idesyde.identification.rules.mixed

import idesyde.identification.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.models.workload.SimplePeriodicWorkload
import idesyde.identification.models.platform.SchedulableNetworkedDigHW
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import org.apache.commons.math3.fraction.BigFraction
import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.decision.Scheduled

class PeriodicTaskToSchedHWIdentRule extends ForSyDeIdentificationRule[PeriodicTaskToSchedHW] {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    var workloadModel = Option.empty[SimplePeriodicWorkload]
    var platformModel = Option.empty[SchedulableNetworkedDigHW]
    identified.foreach(d => {
      d match {
        case dWorkloadModel: SimplePeriodicWorkload    => workloadModel = Option(dWorkloadModel)
        case dPlatformModel: SchedulableNetworkedDigHW => platformModel = Option(dPlatformModel)
        case _                                         =>
      }
    })
    lazy val res = workloadModel.flatMap(workloadModelIn =>
      platformModel.flatMap(platformModelIn =>
        identifyWithDependencies(model, workloadModelIn, platformModelIn)
      )
    )
    if (workloadModel.isDefined && platformModel.isDefined)
      (true, res)
    else (false, Option.empty)

  def identifyWithDependencies(
      model: ForSyDeSystemGraph,
      workloadModel: SimplePeriodicWorkload,
      platformModel: SchedulableNetworkedDigHW
  ): Option[DecisionModel] =
    // alll executables of task are instrumented
    val instrumentedExecutables = workloadModel.tasks.zipWithIndex
      .filter((task, i) => workloadModel.executables(i).forall(InstrumentedExecutable.conforms(_)))
      .map((task, i) => workloadModel.executables(i).map(InstrumentedExecutable.enforce(_)))
    // all processing elems are instrumented
    val instrumentedPEsRange = platformModel.hardware.processingElems
      .filter(pe => InstrumentedProcessingModule.conforms(pe))
      .map(pe => InstrumentedProcessingModule.enforce(pe))
    // all communication elems are instrumented
    val instrumentedCEsRange = platformModel.hardware.communicationElems
      .filter(ce => InstrumentedCommunicationModule.conforms(ce))
      .map(ce => InstrumentedCommunicationModule.enforce(ce))
    // for all tasks, there exists at least one PE where all runnables are executable
    lazy val isExecutable = instrumentedExecutables.zipWithIndex.forall((runnables, i) => {
      instrumentedPEsRange.zipWithIndex.exists((pe, j) => {
        runnables.forall(runnable => {
          // find if there's any matching
          pe.getModalInstructionsPerCycle.values.stream
            .anyMatch(ipc => {
              runnable.getOperationRequirements.values.stream
                .anyMatch(opGroup => ipc.keySet.containsAll(opGroup.keySet))
            })
        })
      })
    })
    // All mappables (tasks, channels) have at least one element to be mapped at
    lazy val isMappable = workloadModel.taskSizes.zipWithIndex.forall((taskSize, i) => {
      platformModel.hardware.storageElems.zipWithIndex.exists((me, j) => {
        taskSize <= me.getSpaceInBits
      })
    }) && workloadModel.channelSizes.zipWithIndex.forall((channelSize, i) => {
      platformModel.hardware.storageElems.zipWithIndex.exists((me, j) => {
        channelSize <= me.getSpaceInBits
      })
    })
    // query all existing mappings
    val taskMappings = workloadModel.tasks.map(task => {
      MemoryMapped
        .safeCast(task)
        .flatMap(memory => {
          memory
            .getMappingHostPort(model)
            .stream
            .mapToInt(platformModel.hardware.storageElems.indexOf(_))
            .filter(_ > -1)
            .findAny
            .toJavaGeneric
        })
        .orElse(-1)
    })
    // now for channels
    val channelMappings = workloadModel.dataBlocks.map(channel => {
      MemoryMapped
        .safeCast(channel)
        .flatMap(memory => {
          memory
            .getMappingHostPort(model)
            .stream
            .mapToInt(platformModel.hardware.storageElems.indexOf(_))
            .filter(_ > -1)
            .findAny
            .toJavaGeneric
        })
        .orElse(-1)
    })
    // now find if any of task are already scheduled (mapped to a processor)
    val taskSchedulings = workloadModel.tasks.map(task => {
      Scheduled
        .safeCast(task)
        .flatMap(scheduled => {
          scheduled
            .getSchedulerPort(model)
            .stream
            .mapToInt(platformModel.schedulers.indexOf(_))
            .filter(_ > -1)
            .findAny
            .toJavaGeneric
        })
        .orElse(-1)
    })
    // finish with construction
    // scribe.debug(s"1 ${instrumentedExecutables.length == workloadModel.tasks.length} &&" +
    //   s"2 ${instrumentedPEsRange.length == platformModel.hardware.processingElems.length} &&" +
    //   s"${isMappable} && ${isExecutable}")
    if (
      instrumentedExecutables.length == workloadModel.tasks.length &&
      instrumentedPEsRange.length == platformModel.hardware.processingElems.length &&
      isMappable && isExecutable
    ) then
      Option(
        PeriodicTaskToSchedHW(
          workloadModel,
          platformModel,
          mappedTasks = taskMappings,
          scheduledTasks = taskSchedulings,
          mappedChannels = channelMappings
        )
      )
    else Option.empty

}

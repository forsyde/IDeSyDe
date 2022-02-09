package idesyde.identification.rules.mixed

import idesyde.identification.IdentificationRule
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

class PeriodicTaskToSchedHWIdentRule extends IdentificationRule {

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
      }
    })
    lazy val res = workloadModel.flatMap(workloadModelIn =>
      platformModel.flatMap(platformModelIn =>
        identifyWithDependencies(model, workloadModelIn, platformModelIn)
      )
    )
    (workloadModel.isDefined && platformModel.isDefined, res)

  def identifyWithDependencies(
      model: ForSyDeSystemGraph,
      workloadModel: SimplePeriodicWorkload,
      platformModel: SchedulableNetworkedDigHW
  ): Option[DecisionModel] =
    // alll executables of task are instrumented
    val instrumentedExecutables = workloadModel.periodicTasks.zipWithIndex
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
    // compute the matrix (lazily)
    lazy val wcets = instrumentedExecutables.zipWithIndex.map((runnables, i) => {
      instrumentedPEsRange.zipWithIndex.map((pe, j) => {
        runnables.foldRight(Option(BigFraction.ZERO))((runnable, sumOpt) => {
          // find the minimum matching
          val bestMatch = pe.getModalInstructionsPerCycle.values.stream
            .flatMap(ipcGroup => {
              runnable.getOperationRequirements.values.stream
                .filter(opGroup => ipcGroup.keySet.equals(opGroup.keySet))
                .map(opGroup => {
                  BigFraction(
                    ipcGroup.entrySet.stream
                      .mapToDouble(ipcEntry => opGroup.get(ipcEntry.getKey) / ipcEntry.getValue)
                      .mapToLong(_.ceil.toLong)
                      .sum,
                    pe.getOperatingFrequencyInHertz
                  )
                })
            })
            .min((f1, f2) => f1.compareTo(f2))
            .toScala
          // fold for the minimum
          sumOpt.flatMap(summed => bestMatch.map(runnableWcet => summed.add(runnableWcet)))
        })
      })
    })
    // compute wctts (lazily)
    lazy val wctts = workloadModel.channels.zipWithIndex.map((channel, i) => {
      instrumentedCEsRange.zipWithIndex.map((ce, j) => {
        BigFraction.ZERO
      })
    })
    // finish with construction
    if (instrumentedExecutables.length == workloadModel.periodicTasks.length &&
    instrumentedPEsRange.length == platformModel.hardware.processingElems.length) then
      Option(
        PeriodicTaskToSchedHW(workloadModel, platformModel, wcets, wctts, Array.emptyIntArray)
      )
    else Option.empty

}

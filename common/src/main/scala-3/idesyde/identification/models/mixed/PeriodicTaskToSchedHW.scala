package idesyde.identification.models.mixed

import idesyde.identification.DecisionModel
import idesyde.identification.models.workload.SimplePeriodicWorkload
import idesyde.identification.models.platform.SchedulableNetworkedDigHW
import forsyde.io.java.core.Vertex
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule

final case class PeriodicTaskToSchedHW(
    val taskModel: SimplePeriodicWorkload,
    val schedHwModel: SchedulableNetworkedDigHW,
    val mappedTasks: Array[Int] = Array.emptyIntArray,
    val scheduledTasks: Array[Int] = Array.emptyIntArray,
    val mappedChannels: Array[Int] = Array.emptyIntArray
) extends DecisionModel:

  val coveredVertexes: Iterable[Vertex] = taskModel.coveredVertexes ++ schedHwModel.coveredVertexes

  lazy val wcets = {
    // alll executables of task are instrumented
    val instrumentedExecutables = taskModel.periodicTasks.zipWithIndex
      .filter((task, i) => taskModel.executables(i).forall(InstrumentedExecutable.conforms(_)))
      .map((task, i) => taskModel.executables(i).map(InstrumentedExecutable.enforce(_)))
    // all processing elems are instrumented
    val instrumentedPEsRange = schedHwModel.hardware.processingElems
      .filter(pe => InstrumentedProcessingModule.conforms(pe))
      .map(pe => InstrumentedProcessingModule.enforce(pe))
    // compute the matrix (lazily)
    instrumentedExecutables.zipWithIndex.map((runnables, i) => {
      instrumentedPEsRange.zipWithIndex.map((pe, j) => {
        runnables.foldRight(Option(BigFraction.ZERO))((runnable, sumOpt) => {
          // find the minimum matching between the runnable and the processing element
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
  }

  lazy val wctts = {
    // all communication elems are instrumented
    val instrumentedCEsRange = schedHwModel.hardware.communicationElems
      .filter(ce => InstrumentedCommunicationModule.conforms(ce))
      .map(ce => InstrumentedCommunicationModule.enforce(ce))
    taskModel.channels.zipWithIndex.map((channel, i) => {
      instrumentedCEsRange.zipWithIndex.map((ce, j) => {
        // get the WCTT in seconds
        BigFraction(
          channel.getElemSizeInBits * channel.getMaxElems * ce.getMaxCyclesPerFlit,
          ce.getFlitSizeInBits * ce.getMaxConcurrentFlits * ce.getOperatingFrequencyInHertz
        )
      })
    })
  }

  val uniqueIdentifier: String = "PeriodicTaskToSchedHW"

end PeriodicTaskToSchedHW

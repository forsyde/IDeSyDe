package idesyde.identification.models.mixed

import math.Ordering.Implicits.infixOrderingOps
import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*

import org.apache.commons.math3.fraction.BigFraction

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.workload.ForSyDePeriodicWorkload
import idesyde.identification.models.platform.SchedulableNetworkedDigHW
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.execution.Task
import idesyde.identification.models.workload.DependentDeadlineMonotonicOrdering
import forsyde.io.java.typed.viewers.nonfunctional.UtilizationBoundedProcessingElem

final case class PeriodicTaskToSchedHW(
    val taskModel: ForSyDePeriodicWorkload,
    val schedHwModel: SchedulableNetworkedDigHW,
    val mappedTasks: Array[Int] = Array.emptyIntArray,
    val scheduledTasks: Array[Int] = Array.emptyIntArray,
    val mappedChannels: Array[Int] = Array.emptyIntArray
) extends ForSyDeDecisionModel:

  val coveredVertexes: Iterable[Vertex] = taskModel.coveredVertexes ++ schedHwModel.coveredVertexes

  lazy val wcets: Array[Array[BigFraction]] = {
    // alll executables of task are instrumented
    val instrumentedExecutables = taskModel.tasks.zipWithIndex
      .filter((task, i) => taskModel.executables(i).forall(InstrumentedExecutable.conforms(_)))
      .map((task, i) => taskModel.executables(i).map(InstrumentedExecutable.enforce(_)))
    // all processing elems are instrumented
    val instrumentedPEsRange = schedHwModel.hardware.processingElems
      .filter(pe => InstrumentedProcessingModule.conforms(pe))
      .map(pe => InstrumentedProcessingModule.enforce(pe))
    // compute the matrix (lazily)
    instrumentedExecutables.zipWithIndex.map((runnables, i) => {
      instrumentedPEsRange.zipWithIndex.map((pe, j) => {
        runnables.foldRight(BigFraction.ZERO)((runnable, sum) => {
          // find the minimum matching between the runnable and the processing element
          pe.getModalInstructionsPerCycle.values.stream
            .flatMap(ipcGroup => {
              runnable.getOperationRequirements.values.stream
                .filter(opGroup => ipcGroup.keySet.containsAll(opGroup.keySet))
                .map(opGroup => {
                  opGroup.entrySet.stream
                    .map(opEntry =>
                      BigFraction(opEntry.getValue)
                        .divide(BigFraction(ipcGroup.get(opEntry.getKey)))
                    )
                    .reduce(BigFraction.ZERO, (f1, f2) => f1.add(f2))
                    .divide(pe.getOperatingFrequencyInHertz)
                })
            })
            .min((f1, f2) => f1.compareTo(f2))
            .orElse(BigFraction.MINUS_ONE)
        })
      })
    })
  }

  lazy val wctts: Array[Array[BigFraction]] = {
    // all communication elems are instrumented
    val instrumentedCEsRange = schedHwModel.hardware.communicationElems
      .filter(ce => InstrumentedCommunicationModule.conforms(ce))
      .map(ce => InstrumentedCommunicationModule.enforce(ce))
    taskModel.dataBlocks.zipWithIndex.map((channel, i) => {
      instrumentedCEsRange.zipWithIndex.map((ce, j) => {
        // get the WCTT in seconds
        BigFraction(
          channel.getMaxSizeInBits * ce.getMaxCyclesPerFlit,
          ce.getFlitSizeInBits * ce.getMaxConcurrentFlits * ce.getOperatingFrequencyInHertz
        )
      })
    })
  }

  lazy val conservativeWcct: Array[Array[Array[BigFraction]]] = {
    val endPoints = schedHwModel.hardware.processingElems ++ schedHwModel.hardware.storageElems
    taskModel.dataBlocks.zipWithIndex.map((c, k) => {
      schedHwModel.hardware.platformElements.zipWithIndex.map((pi, i) => {
        schedHwModel.hardware.platformElements.zipWithIndex.map((pj, j) => {
          val t = schedHwModel.hardware.maxTraversalTimePerBit(i)(j)
          if (t.compareTo(BigFraction.MINUS_ONE) > 0) then t.multiply(c.getMaxSizeInBits)
          else t
        })
      })
    })
    Array.empty
  }

  lazy val maxUtilization: Array[BigFraction] = schedHwModel.hardware.processingElems.map(pe =>
    UtilizationBoundedProcessingElem
      .safeCast(pe)
      .map(boundedPe =>
        BigFraction(boundedPe.getMaxUtilizationNumerator, boundedPe.getMaxUtilizationDenominator)
      )
      .orElse(BigFraction.ONE)
  )

  def exactRMSchedulingPoints(using Ordering[Task]): Array[Array[BigFraction]] =
    taskModel.tasks.zipWithIndex
      .map((task, i) => {
        taskModel.tasks
          .filter(hpTask => hpTask > task)
          .zipWithIndex
          .flatMap((hpTask, j) => {
            (0 until taskModel.tasksNumInstances(j))
              .map(k => {
                taskModel.offsets(j).add(taskModel.periods(j).multiply(k))
              })
              .filterNot(t => t.equals(taskModel.hyperPeriod))
          })
      })

  def sufficientRMSchedulingPoints(using Ordering[Task]): Array[Array[BigFraction]] =
    taskModel.tasks.zipWithIndex
      .map((task, i) => {
        taskModel.tasks
          .filter(hpTask => hpTask > task)
          .zipWithIndex
          .map((hpTask, j) => {
            taskModel
              .periods(j)
              .multiply(taskModel.periods(i).divide(taskModel.periods(j)).doubleValue.floor.toLong)
          })
      })

  //scribe.debug(sufficientRMSchedulingPoints(using DependentDeadlineMonotonicOrdering(taskModel)).mkString("[", ",", "]"))

  val uniqueIdentifier: String = "PeriodicTaskToSchedHW"

end PeriodicTaskToSchedHW

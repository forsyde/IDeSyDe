package idesyde.identification.models.mixed

import math.Ordering.Implicits.infixOrderingOps
import scala.jdk.OptionConverters.*
import scala.jdk.StreamConverters.*
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
import idesyde.utils.MultipliableFractional

final case class PeriodicTaskToSchedHW(
    val taskModel: ForSyDePeriodicWorkload,
    val schedHwModel: SchedulableNetworkedDigHW,
    val mappedTasks: Array[Int] = Array.emptyIntArray,
    val scheduledTasks: Array[Int] = Array.emptyIntArray,
    val mappedChannels: Array[Int] = Array.emptyIntArray
)(using MultipliableFractional[BigFraction]) extends ForSyDeDecisionModel:

  val coveredVertexes: Iterable[Vertex] = taskModel.coveredVertexes ++ schedHwModel.coveredVertexes

  lazy val wcets: Array[Array[BigFraction]] = {
    // alll executables of task are instrumented
    // scribe.debug(taskModel.executables.mkString("[", ",", "]"))
    // compute the matrix (lazily)
    // scribe.debug(taskModel.taskComputationNeeds.mkString(", "))
    taskModel.taskComputationNeeds.map(needs => {
      // scribe.debug(needs.mkString(","))
      schedHwModel.hardware.processingElems.map(pe => {
        InstrumentedProcessingModule
          .safeCast(pe)
          .map(peInst => {
            // scribe.debug(peInst.getModalInstructionsPerCycle.asScala.mkString(", "))
            peInst.getModalInstructionsPerCycle.values.stream
              .flatMap(ipcGroup => {
                needs.values
                  // due to how it is implemented in java, the contains all check if the parameter
                  // is a subset of the callee, and not vice-versa
                  .map(opGroup => {
                    opGroup})
                  .filter(opGroup => opGroup.keySet.forall(opName => ipcGroup.containsKey(opName)))
                  .map(opGroup => {
                    opGroup
                      .map((opKey, opValue) =>
                        BigFraction(opValue)
                          .divide(BigFraction(ipcGroup.get(opKey)))
                      )
                      .sum
                      // .reduce((f1, f2) => f1.add(f2))
                      .divide(pe.getOperatingFrequencyInHertz)
                  })
                  .asJavaSeqStream
              })
              .filter(f => f.compareTo(BigFraction.MINUS_ONE) > 0)
              .min((f1, f2) => f1.compareTo(f2))
              .orElse(BigFraction.MINUS_ONE)
          })
          .orElse(BigFraction.ZERO)

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

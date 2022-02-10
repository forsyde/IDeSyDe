package idesyde.identification.choco

import idesyde.identification.interfaces.ChocoCPDecisionModel

import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import org.chocosolver.solver.Model
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import idesyde.identification.models.workload.PeriodicWorkload

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*


final case class PeriodicTaskToSchedHWChoco(
    val sourceDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPDecisionModel:

  val coveredVertexes = sourceDecisionModel.coveredVertexes

  // section for time multiplier calculation
  var multiplierForNoDenominator = absoluteDeadlines()
    .map(_.getDenominatorAsLong)
    .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))
  // while there are significant zeros that can be taken away
  var tenthDivision = 1L
  while (absoluteDeadlines(multiplierForNoDenominator).min.getNumeratorAsLong % tenthDivision == 0)
    tenthDivision *= 10L
  val multiplier = multiplierForNoDenominator / tenthDivision

  // do the same for memory numbers
  var memoryMultipler = 1L
  while (allMemorySizeNumbers().min % 10 == 0)
    memoryMultipler *= 10L
  
  // build the model so that it can be acessed later
  val model = Model()
  // the true decision variables
  val taskExecution = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "exe_" + t.getViewedVertex.getIdentifier,
      sourceDecisionModel
        .wcets(i)
        .zipWithIndex
        .filter((p, i) => p.compareTo(BigFraction.MINUS_ONE) > 0)
        .map((p, i) => i) // keep the processors where WCEt is defined
    )
  )
  val taskMapping = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "map_" + t.getViewedVertex.getIdentifier,
      0,
      sourceDecisionModel.schedHwModel.hardware.storageElems.size - 1
    )
  )
  val channelMapping = sourceDecisionModel.taskModel.channels.zipWithIndex.map((c, i) =>
    model.intVar(
      "map_" + c.getViewedVertex.getIdentifier,
      0,
      sourceDecisionModel.schedHwModel.hardware.storageElems.size - 1
    )
  )
  // auxiliary variables
  val responseTimes =
    sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
      model.intVar(
        "rt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        sourceDecisionModel
          .wcets(i)
          .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
          .min
          .multiply(multiplier)
          .getNumeratorAsInt,
        sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).getNumeratorAsInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val wcExecution = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "exe_wc" + t.getViewedVertex.getIdentifier,
      sourceDecisionModel
        .wcets(i)
        .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
        .min
        .multiply(multiplier)
        .getNumeratorAsInt,
      sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).getNumeratorAsInt,
      true
    )
  )
  val wcFetch = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "fetch_wc" + t.getViewedVertex.getIdentifier,
      0,
      sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).getNumeratorAsInt,
      true
    )
  )
  val wcInput = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "input_wc" + t.getViewedVertex.getIdentifier,
      0,
      sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).getNumeratorAsInt,
      true
    )
  )
  val wcOutput = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "output_wc" + t.getViewedVertex.getIdentifier,
      0,
      sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).getNumeratorAsInt,
      true
    )
  )
  val wcet = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
    wcExecution(i).add(wcFetch(i)).add(wcInput(i)).add(wcOutput(i)).intVar
  )
  
  def chocoModel: Model = model

  def rebuildFromChocoOutput(output: Model): ForSyDeSystemGraph =
    ForSyDeSystemGraph()

  def absoluteDeadlines(multiplier: Long = 1L) =
    sourceDecisionModel.taskModel.periodicTasks.zipWithIndex
      .map((t, i) =>
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(sourceDecisionModel.taskModel.tasksNumInstancesArray(i))
          .add(sourceDecisionModel.taskModel.offsets(i))
      )

  def allMemorySizeNumbers() = 
    (sourceDecisionModel.schedHwModel.hardware.storageElems.map(_.getSpaceInBits.toLong) ++
    sourceDecisionModel.taskModel.channelSizes ++
    sourceDecisionModel.taskModel.taskSizes)

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

end PeriodicTaskToSchedHWChoco

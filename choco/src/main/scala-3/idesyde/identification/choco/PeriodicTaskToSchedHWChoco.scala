package idesyde.identification.choco

import idesyde.identification.interfaces.ChocoCPDecisionModel

import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import org.chocosolver.solver.Model
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.fraction.BigFraction

final case class PeriodicTaskToSchedHWChoco(
    val sourceDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPDecisionModel:

  val coveredVertexes = sourceDecisionModel.coveredVertexes

  var multiplier =
    sourceDecisionModel.taskModel.periodicTasks.zipWithIndex
      .map((t, i) =>
        (sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .add(1))
          .multiply(sourceDecisionModel.taskModel.tasksNumInstancesArray(i))
          .add(sourceDecisionModel.taskModel.offsets(i))
      )
      //.flatMap(j => Seq(j.trigger, j.deadline))
      .map(_.getDenominatorAsLong)
      .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))

  def chocoModel: Model = {
    val model = Model()
    // the true decision variables
    val taskExecution = sourceDecisionModel.taskModel.periodicTasks.zipWithIndex.map((t, i) =>
      model.intVar(
        "exe_" + t.getViewedVertex.getIdentifier,
        sourceDecisionModel
          .worstCaseExecution(i)
          .zipWithIndex
          .filter((p, i) => p.isDefined)
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
            .worstCaseExecution(i)
            .min
            .getOrElse(BigFraction.ZERO)
            .multiply(multiplier)
            .getNumeratorAsInt,
          sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).getNumeratorAsInt,
          true // keeping only bounds for the response time is enough and better
        )
      )
    model
  }

  def rebuildFromChocoOutput(output: Model, originalModel: ForSyDeSystemGraph): ForSyDeSystemGraph =
    ForSyDeSystemGraph()

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

end PeriodicTaskToSchedHWChoco

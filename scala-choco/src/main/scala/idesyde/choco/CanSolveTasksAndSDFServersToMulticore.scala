package idesyde.choco

import idesyde.identification.common.models.mixed.TasksAndSDFServerToMultiCore
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution

trait CanSolveTasksAndSDFServersToMulticore
    extends ChocoExplorable[TasksAndSDFServerToMultiCore]
    with HasDiscretizationToIntegers {

  def buildChocoModel(
      m: TasksAndSDFServerToMultiCore,
      timeResolution: Long,
      memoryResolution: Long
  ): Model = {
    val chocoModel = Model()
    val execMax    = m.wcets.flatten.max
    val commMax    = m.platform.hardware.maxTraversalTimePerBit.flatten.map(_.toDouble).max
    val timeValues =
      m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
        .map(
          _.toDouble
        ) ++ m.tasksAndSDFs.workload.periods ++ m.wcets.flatten ++ m.tasksAndSDFs.workload.relativeDeadlines
    val memoryValues = m.platform.hardware.storageSizes ++ m.tasksAndSDFs.sdfApplication.sdfMessages
      .map((src, _, _, mSize, p, c, tok) =>
        mSize
      ) ++ m.tasksAndSDFs.workload.messagesMaxSizes ++ m.tasksAndSDFs.workload.processSizes
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (timeResolution > Int.MaxValue) Int.MaxValue else timeResolution.toInt,
    //     if (memoryResolution > Int.MaxValue) Int.MaxValue else memoryResolution.toInt
    //   )
    def double2int(s: Double) = discretized(
      if (timeResolution > Int.MaxValue) Int.MaxValue
      else if (timeResolution <= 0L) timeValues.size * 100
      else timeResolution.toInt,
      timeValues.sum
    )(s)
    given Fractional[Long] = HasDiscretizationToIntegers.ceilingLongFractional
    def long2int(l: Long) = discretized(
      if (memoryResolution > Int.MaxValue) Int.MaxValue
      else if (memoryResolution <= 0L) memoryValues.size * 100
      else memoryResolution.toInt,
      memoryValues.max
    )(l)
    chocoModel
  }

  def rebuildDecisionModel(
      m: TasksAndSDFServerToMultiCore,
      solution: Solution,
      timeResolution: Long,
      memoryResolution: Long
  ): TasksAndSDFServerToMultiCore = ???
}

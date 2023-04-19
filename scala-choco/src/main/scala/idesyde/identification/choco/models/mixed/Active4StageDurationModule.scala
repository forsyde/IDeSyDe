package idesyde.identification.choco.models.mixed

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.`extension`.Tuples
import org.chocosolver.solver.Model
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.utils.HasUtils

class Active4StageDurationModule(
    val chocoModel: Model,
    val tasksAndPlatform: PeriodicWorkloadToPartitionedSharedMultiCore,
    val taskExecution: Array[IntVar],
    val taskMapping: Array[IntVar],
    val dataMapping: Array[IntVar],
    val processingElemsVirtualChannelInCommElem: Array[Array[IntVar]],
    val timeMultiplier: Long = 1L,
    val memoryDivider: Long = 1L
) extends ChocoModelMixin()
    with HasUtils {

  private val executionTimes =
    tasksAndPlatform.wcets.map(_.map(_ * timeMultiplier).map(_.ceil.toInt).toArray).toArray
  private val taskSizes =
    tasksAndPlatform.workload.processSizes
      .map(ceil(_, memoryDivider))
      .map(_.toInt)
      .toArray
  private val messageSizes =
    tasksAndPlatform.workload.messagesMaxSizes
      .map(ceil(_, memoryDivider))
      .map(_.toInt)
      .toArray
  private val storageSizes =
    tasksAndPlatform.platform.hardware.storageSizes
      .map(ceil(_, memoryDivider))
      .map(_.toInt)
      .toArray

  private val taskTravelTime = tasksAndPlatform.workload.processSizes
    .map(d =>
      tasksAndPlatform.platform.hardware.communicationElementsBitPerSecPerChannel
        .map(b =>
          // TODO: check if this is truly conservative (pessimistic) or not
          (d / b / timeMultiplier / memoryDivider).ceil.toInt
        )
        .toArray
    )
    .toArray

  private val dataTravelTime = tasksAndPlatform.workload.messagesMaxSizes
    .map(d =>
      tasksAndPlatform.platform.hardware.communicationElementsBitPerSecPerChannel
        .map(b =>
          // TODO: check if this is truly conservative (pessimistic) or not
          (d / b / (timeMultiplier) / (memoryDivider)).ceil.toInt
        )
        .toArray
    )
    .toArray

  private val tasks         = tasksAndPlatform.workload.processes
  private val processors    = tasksAndPlatform.platform.hardware.processingElems
  private val memories      = tasksAndPlatform.platform.hardware.storageElems
  private val communicators = tasksAndPlatform.platform.hardware.communicationElems

  val durationsExec = tasks.zipWithIndex
    .map((t, i) =>
      chocoModel.intVar(
        s"exe_wc($t)",
        executionTimes(i).filter(_ > 0)
      )
    )
    .toArray
  val durationsFetch = tasks.zipWithIndex
    .map((t, i) =>
      chocoModel.intVar(
        s"fetch_wc($i)",
        0,
        taskTravelTime(i).sum,
        true
      )
    )
    .toArray
  val durationsRead = tasks.zipWithIndex
    .map((t, i) =>
      chocoModel.intVar(
        s"input_wc($i)",
        0,
        dataTravelTime(i).sum,
        true
      )
    )
    .toArray
  val durationsWrite = tasks.zipWithIndex
    .map((t, i) =>
      chocoModel.intVar(
        s"output_wc($i)",
        0,
        dataTravelTime(i).sum,
        true
      )
    )
    .toArray
  val durations = tasks.zipWithIndex
    .map((t, i) =>
      chocoModel.sum(
        s"dur($t)",
        durationsFetch(i),
        durationsRead(i),
        durationsExec(i),
        durationsWrite(i)
      )
    )
    .toArray

  val totalVCPerCommElem = communicators.zipWithIndex
    .map((c, i) =>
      chocoModel.intVar(
        s"vcTotal($c)",
        0,
        tasksAndPlatform.platform.hardware.communicationElementsMaxChannels(i) * processors.size,
        true
      )
    )
    .toArray

  private def taskReadsData(t: Int)(c: Int): Long =
    tasksAndPlatform.workload.dataGraph
      .find((a, b, _) =>
        a == tasksAndPlatform.workload.tasks(t) && b == tasksAndPlatform.workload
          .dataChannels(c)
      )
      .map((_, _, l) => l)
      .getOrElse(0L)
  private def taskWritesData(t: Int)(c: Int): Long =
    tasksAndPlatform.workload.dataGraph
      .find((a, b, _) =>
        b == tasksAndPlatform.workload.tasks(t) && a == tasksAndPlatform.workload
          .dataChannels(c)
      )
      .map((_, _, l) => l)
      .getOrElse(0L)

  def postActive4StageDurationsConstraints(): Unit = {
    // deduced parameters
    // auxiliary local variables
    // scribe.debug(communicators.map(c => dataTravelTime.map(t => t(c)).sum).mkString(", "))
    // basic equality for durations
    for ((t, i) <- tasks.zipWithIndex; (p, j) <- processors.zipWithIndex) {
      // sum of all durations
      // for the execution times
      chocoModel.ifThen(
        chocoModel.arithm(taskExecution(i), "=", j),
        chocoModel.arithm(durationsExec(i), "=", executionTimes(i)(j))
      )
    }
    // now for the communications
    // at least one path needs to be satisfied for isntruction fetching
    // val pathMatrix = allowedProc2MemoryDataPaths(p)(mem).map(path =>
    //   communicators.map(c => if (path.contains(c)) then 1 else -1).toArray
    // )
    // val pathTuples = Tuples(pathMatrix, true)
    // pathTuples.setUniversalValue(-1)
    for (
      (exec, _) <- taskExecution.zipWithIndex;
      (mapp, _) <- taskMapping.zipWithIndex;
      (p, i)    <- tasksAndPlatform.platform.hardware.processingElems.zipWithIndex;
      (m, j)    <- tasksAndPlatform.platform.hardware.storageElems.zipWithIndex;
      ce        <- tasksAndPlatform.platform.hardware.computedPaths(p)(m);
      k = tasksAndPlatform.platform.hardware.communicationElems.indexOf(ce)
    ) {
      chocoModel.ifThen(
        exec.eq(i).and(mapp.eq(j)).decompose(),
        // at least one of the paths must be taken
        processingElemsVirtualChannelInCommElem(i)(k).gt(0).decompose()
      )
    }
    // the same for data fetching or writting
    // they are symemtric in terms of constraints because the platform is assumed
    // in this module to have bidirectional links
    for (
      (exec, t) <- taskExecution.zipWithIndex;
      (mapp, c) <- dataMapping.zipWithIndex;
      if taskReadsData(t)(c) > 0 || taskReadsData(t)(c) > 0;
      (p, i) <- tasksAndPlatform.platform.hardware.processingElems.zipWithIndex;
      (m, j) <- tasksAndPlatform.platform.hardware.storageElems.zipWithIndex;
      ce     <- tasksAndPlatform.platform.hardware.computedPaths(p)(m);
      k = tasksAndPlatform.platform.hardware.communicationElems.indexOf(ce)
    ) {
      chocoModel.ifThen(
        exec.eq(i).and(mapp.eq(j)).decompose(),
        // at least one of the paths must be taken
        processingElemsVirtualChannelInCommElem(i)(k).gt(0).decompose()
      )
    }
    // now for timing, make sure to account everything conditionally on the VCs allocated
    for (
      (exec, t) <- taskExecution.zipWithIndex;
      (mapp, _) <- taskMapping.zipWithIndex;
      (p, i)    <- tasksAndPlatform.platform.hardware.processingElems.zipWithIndex;
      (m, j)    <- tasksAndPlatform.platform.hardware.storageElems.zipWithIndex;
      path    = tasksAndPlatform.platform.hardware.computedPaths(p)(m);
      pathIdx = path.map(tasksAndPlatform.platform.hardware.communicationElems.indexOf(_)).toArray
    ) {
      chocoModel.ifThen(
        exec.eq(i).and(mapp.eq(j)).decompose(),
        // at least one of the paths must be taken
        chocoModel.scalar(
          pathIdx.map(totalVCPerCommElem(_)),
          pathIdx.map(taskTravelTime(t)(_)),
          "=",
          durationsFetch(t)
        )
      )
    }
    for (
      (exec, t) <- taskExecution.zipWithIndex;
      (mapp, c) <- dataMapping.zipWithIndex;
      (p, i)    <- tasksAndPlatform.platform.hardware.processingElems.zipWithIndex;
      (m, j)    <- tasksAndPlatform.platform.hardware.storageElems.zipWithIndex;
      path    = tasksAndPlatform.platform.hardware.computedPaths(p)(m);
      pathIdx = path.map(tasksAndPlatform.platform.hardware.communicationElems.indexOf(_)).toArray
    ) {
      if (taskReadsData(t)(c) > 0) {
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.scalar(
            pathIdx.map(totalVCPerCommElem(_)),
            pathIdx.map(taskTravelTime(t)(_)),
            "=",
            durationsRead(t)
          )
        )
      } else if (taskWritesData(t)(c) > 0) {
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.scalar(
            pathIdx.map(totalVCPerCommElem(_)),
            pathIdx.map(taskTravelTime(t)(_)),
            "=",
            durationsWrite(t)
          )
        )
      }
    }
    // the load of each communicatior
    for ((c, i) <- communicators.zipWithIndex) {
      chocoModel
        .sum(
          processingElemsVirtualChannelInCommElem.map(vc => vc(i)),
          "=",
          totalVCPerCommElem(i)
        )
        .post()
    }
  }
}

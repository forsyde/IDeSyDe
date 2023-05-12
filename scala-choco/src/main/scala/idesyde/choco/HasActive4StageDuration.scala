package idesyde.choco

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.`extension`.Tuples
import org.chocosolver.solver.Model
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.utils.HasUtils

trait HasActive4StageDuration extends HasUtils {

  // private val executionTimes =
  //   tasksAndPlatform.wcets.map(_.map(_ * timeMultiplier).map(_.ceil.toInt).toArray).toArray
  // private val taskSizes =
  //   tasksAndPlatform.workload.processSizes
  //     .map(ceil(_, memoryDivider))
  //     .map(_.toInt)
  //     .toArray
  // private val messageSizes =
  //   tasksAndPlatform.workload.messagesMaxSizes
  //     .map(ceil(_, memoryDivider))
  //     .map(_.toInt)
  //     .toArray

  // private val taskTravelTime = tasksAndPlatform.workload.processSizes
  //   .map(d =>
  //     tasksAndPlatform.platform.hardware.communicationElementsBitPerSecPerChannel
  //       .map(b =>
  //         // TODO: check if this is truly conservative (pessimistic) or not
  //         (d / b / timeMultiplier / memoryDivider).ceil.toInt
  //       )
  //       .toArray
  //   )
  //   .toArray

  // private val dataTravelTime = tasksAndPlatform.workload.messagesMaxSizes
  //   .map(d =>
  //     tasksAndPlatform.platform.hardware.communicationElementsBitPerSecPerChannel
  //       .map(b =>
  //         // TODO: check if this is truly conservative (pessimistic) or not
  //         (d / b / (timeMultiplier) / (memoryDivider)).ceil.toInt
  //       )
  //       .toArray
  //   )
  //   .toArray

  def postActive4StageDurationsConstraints(
      chocoModel: Model,
      executionTimes: Array[Array[Int]],
      taskTravelTime: Array[Array[Int]],
      dataTravelTime: Array[Array[Int]],
      communicationElementsMaxChannels: Vector[Int],
      computedPaths: (Int) => (Int) => Iterable[Int],
      taskReadsData: (Int) => (Int) => Long,
      taskWritesData: (Int) => (Int) => Long,
      taskExecution: Array[IntVar],
      taskMapping: Array[IntVar],
      dataMapping: Array[IntVar],
      processingElemsVirtualChannelInCommElem: Array[Array[IntVar]]
  ): (Array[IntVar], Array[IntVar], Array[IntVar], Array[IntVar], Array[IntVar], Array[IntVar]) = {
    val tasks        = 0 until executionTimes.length
    val dataChannels = 0 until dataTravelTime.length
    val processors   = 0 until executionTimes.head.length
    val memories = taskMapping.map(_.getLB().toInt).min until taskMapping.map(_.getUB().toInt).max
    val communicators = 0 until communicationElementsMaxChannels.length
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
    val durationsReadPerSig = tasks.zipWithIndex
      .map((t, i) =>
        dataChannels.map(j =>
          if (taskReadsData(i)(j) > 0) {
            chocoModel.intVar(
              s"input_wc($i)",
              0,
              dataTravelTime(j).sum,
              true
            )
          } else {
            chocoModel.intVar(0)
          }
        )
      )
      .toArray
    val durationsWritePerSig = tasks.zipWithIndex
      .map((t, i) =>
        dataChannels.map(j =>
          if (taskWritesData(i)(j) > 0) {
            chocoModel.intVar(
              s"output_wc($i, $j)",
              0,
              dataTravelTime(j).sum,
              true
            )
          } else {
            chocoModel.intVar(0)
          }
        )
      )
      .toArray
    val totalVCPerCommElem = communicators
      .map(c =>
        chocoModel.intVar(
          s"vcTotal($c)",
          0,
          communicationElementsMaxChannels(c) * processors.size,
          true
        )
      )
      .toArray

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
      i         <- processors;
      j         <- memories;
      k         <- computedPaths(i)(j)
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
      i <- processors;
      j <- memories;
      k <- computedPaths(i)(j)
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
      i         <- processors;
      j         <- memories;
      pathIdx = computedPaths(i)(j).toArray
      // pathIdx = path.map(tasksAndPlatform.platform.hardware.communicationElems.indexOf(_)).toArray
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
      i         <- processors;
      j         <- memories;
      pathIdx = computedPaths(i)(j).toArray
    ) {
      if (taskReadsData(t)(c) > 0) {
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.scalar(
            pathIdx.map(totalVCPerCommElem(_)),
            pathIdx.map(dataTravelTime(c)(_)),
            "=",
            durationsReadPerSig(t)(c)
          )
        )
      } else if (taskWritesData(t)(c) > 0) {
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.scalar(
            pathIdx.map(totalVCPerCommElem(_)),
            pathIdx.map(dataTravelTime(c)(_)),
            "=",
            durationsWritePerSig(t)(c)
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
    val durationsRead = tasks.zipWithIndex
      .map((t, i) => chocoModel.sum(s"input_wc($i)", durationsReadPerSig(i): _*))
      .toArray
    val durationsWrite = tasks.zipWithIndex
      .map((t, i) => chocoModel.sum(s"output_wc($i)", durationsWritePerSig(i): _*))
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
    (durationsExec, durationsFetch, durationsRead, durationsWrite, durations, totalVCPerCommElem)
  }
}

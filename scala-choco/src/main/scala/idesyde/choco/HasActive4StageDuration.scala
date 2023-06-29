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
      communicationElementsMaxChannels: Vector[Int],
      communicationElementsFrameSize: Vector[Int],
      taskFetchTimePerChannel: (Int) => (Int) => Int,
      taskReadsDataTimePerChannel: (Int) => (Int) => (Int) => Int,
      taskWritesDataTimePerChannel: (Int) => (Int) => (Int) => Int,
      computedPaths: (Int) => (Int) => Iterable[Int],
      taskExecution: Array[IntVar],
      taskMapping: Array[IntVar],
      dataMapping: Array[IntVar],
      processingElemsVirtualChannelInCommElem: Array[Array[IntVar]]
  ): (Array[IntVar], Array[IntVar], Array[IntVar], Array[IntVar], Array[IntVar], Array[IntVar]) = {
    val tasks        = 0 until executionTimes.length
    val dataChannels = 0 until dataMapping.length
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
          communicators.map(ce => taskFetchTimePerChannel(i)(ce)).sum,
          true
        )
      )
      .toArray
    val durationsReadPerSig = tasks.zipWithIndex
      .map((t, i) =>
        dataChannels.map(j =>
          if (communicators.exists(ce => taskReadsDataTimePerChannel(i)(j)(ce) > 0)) {
            chocoModel.intVar(
              s"input_wc($i, $j)",
              0,
              communicators.map(ce => taskReadsDataTimePerChannel(i)(j)(ce)).sum,
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
          if (communicators.exists(ce => taskWritesDataTimePerChannel(i)(j)(ce) > 0)) {
            chocoModel.intVar(
              s"output_wc($i, $j)",
              0,
              communicators.map(ce => taskWritesDataTimePerChannel(i)(j)(ce)).sum,
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
          s"totalVCPerCommElem($c)",
          0,
          communicationElementsMaxChannels(c),
          true
        )
      )
      .toArray

    val minVCInPath = processors.map(src =>
      processors.map(dst =>
        if (computedPaths(src)(dst).isEmpty) {
          chocoModel.intVar(
            s"minVCInPath($src, $dst)",
            0
          )
        } else {
          chocoModel.min(
            s"minVCInPath($src, $dst)",
            computedPaths(src)(dst).map(totalVCPerCommElem(_)).toArray: _*
          )
        }
      )
    )

    // deduced parameters
    // auxiliary local variables
    // scribe.debug(communicators.map(c => dataTravelTime.map(t => t(c)).sum).mkString(", "))
    // basic equality for durations
    for ((t, i) <- tasks.zipWithIndex; (p, j) <- processors.zipWithIndex) {
      // sum of all durations
      // for the execution times
      if (executionTimes(i)(j) > 0) {
        chocoModel.ifThen(
          chocoModel.arithm(taskExecution(i), "=", j),
          chocoModel.arithm(durationsExec(i), "=", executionTimes(i)(j))
        )
      } else {
        chocoModel.arithm(taskExecution(i), "!=", j).post()
      }
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
      i         <- processors;
      j         <- memories;
      k         <- computedPaths(i)(j);
      if taskReadsDataTimePerChannel(t)(c)(k) > 0 || taskReadsDataTimePerChannel(t)(c)(k) > 0
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
        chocoModel.sum(
          pathIdx
            .map(ce =>
              chocoModel.intAffineView(
                communicationElementsFrameSize(ce),
                minVCInPath(i)(j),
                taskFetchTimePerChannel(t)(ce)
              )
            ),
          "=",
          minVCInPath(i)(j).mul(durationsFetch(t)).intVar()
        )
      )
      chocoModel.ifThen(
        exec.eq(i).and(mapp.eq(j)).decompose(),
        // at least one of the paths must be taken
        chocoModel.arithm(
          durationsFetch(t),
          "<=",
          pathIdx
            .map(ce =>
              communicationElementsFrameSize(ce)
              +
              taskFetchTimePerChannel(t)(ce)
            )
            .sum
        )
      )
      chocoModel.ifThen(
        exec.eq(i).and(mapp.eq(j)).decompose(),
        // at least one of the paths must be taken
        chocoModel.arithm(
          durationsFetch(t),
          ">=",
          pathIdx
            .map(ce =>
              communicationElementsFrameSize(ce)
              +
              taskFetchTimePerChannel(t)(ce) / communicationElementsMaxChannels(ce)
            )
            .sum
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
      if (pathIdx.forall(ce => taskReadsDataTimePerChannel(t)(c)(ce) > 0)) {
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.sum(
            pathIdx
              .map(ce =>
                chocoModel.intAffineView(
                  communicationElementsFrameSize(ce),
                  minVCInPath(i)(j),
                  taskReadsDataTimePerChannel(t)(c)(ce)
                )
              ),
            "=",
            minVCInPath(i)(j).mul(durationsReadPerSig(t)(c)).intVar()
          )
          // chocoModel.scalar(
          //   pathIdx.map(totalVCPerCommElem(_)),
          //   pathIdx.map(dataTravelTime(c)(_)),
          //   "=",
          //   durationsReadPerSig(t)(c)
          // )
        )
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.arithm(
            durationsReadPerSig(t)(c),
            "<=",
            pathIdx
              .map(ce =>
                communicationElementsFrameSize(ce)
                +
                taskReadsDataTimePerChannel(t)(c)(ce)
              )
              .sum
          )
        )
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.arithm(
            durationsReadPerSig(t)(c),
            ">=",
            pathIdx
              .map(ce =>
                communicationElementsFrameSize(ce)
                +
                taskReadsDataTimePerChannel(t)(c)(ce) / communicationElementsMaxChannels(ce)
              )
              .sum
          )
        )
      } else if (pathIdx.forall(ce => taskWritesDataTimePerChannel(t)(c)(ce) > 0)) {
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.sum(
            pathIdx
              .map(ce =>
                chocoModel.intAffineView(
                  communicationElementsFrameSize(ce),
                  minVCInPath(i)(j),
                  taskWritesDataTimePerChannel(t)(c)(ce)
                )
              ),
            "=",
            minVCInPath(i)(j).mul(durationsWritePerSig(t)(c)).intVar()
          )
          // chocoModel.scalar(
          //   pathIdx.map(totalVCPerCommElem(_)),
          //   pathIdx.map(dataTravelTime(c)(_)),
          //   "=",
          //   durationsWritePerSig(t)(c)
          // )
        )
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.arithm(
            durationsWritePerSig(t)(c),
            "<=",
            pathIdx
              .map(ce =>
                communicationElementsFrameSize(ce)
                +
                taskWritesDataTimePerChannel(t)(c)(ce)
              )
              .sum
          )
        )
        chocoModel.ifThen(
          exec.eq(i).and(mapp.eq(j)).decompose(),
          // at least one of the paths must be taken
          chocoModel.arithm(
            durationsWritePerSig(t)(c),
            ">=",
            pathIdx
              .map(ce =>
                communicationElementsFrameSize(ce)
                +
                taskWritesDataTimePerChannel(t)(c)(ce) / communicationElementsMaxChannels(ce)
              )
              .sum
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

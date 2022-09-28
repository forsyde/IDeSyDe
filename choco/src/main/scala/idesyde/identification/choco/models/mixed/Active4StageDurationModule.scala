package idesyde.identification.choco.models.mixed

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.`extension`.Tuples
import org.chocosolver.solver.Model

class Active4StageDurationModule(
  val chocoModel: Model,
  val executionTimes: Array[Array[Int]],
  val taskTravelTime: Array[Array[Int]],
  val dataTravelTime: Array[Array[Int]],
  val allowedProc2MemoryDataPaths: Array[Array[Array[Array[Int]]]],
  val taskReadsData: Array[Array[Boolean]],
  val taskWritesData: Array[Array[Boolean]],

  val taskExecution: Array[IntVar],
  val taskMapping: Array[IntVar],
  val dataMapping: Array[IntVar],
  val taskCommunicationMapping: Array[Array[BoolVar]],
  val dataCommunicationMapping: Array[Array[BoolVar]],
) extends ChocoModelMixin {

  private val tasks = (0 until executionTimes.size).toArray
  private val processors    = (0 until allowedProc2MemoryDataPaths.length).toArray
  private val memories      = (0 until allowedProc2MemoryDataPaths.head.length).toArray
  private val communicators = (0 until dataCommunicationMapping.head.length).toArray

  val durationsExec = executionTimes.zipWithIndex.map((w, i) =>
    w.zipWithIndex.map((p, j) =>
      chocoModel.intVar(
        s"exe_wc($i, $j)",
        if (p > 0) then
          Array(
            0,
            p
          )
        else Array(0)
      )
    )
  )
  val durationsFetch = executionTimes.zipWithIndex.map((w, i) =>
    w.zipWithIndex.map((p, j) =>
      chocoModel.intVar(
        s"fetch_wc($i, $j)",
        0,
        taskTravelTime(i).sum,
        true
      )
    )
  )
  val durationsRead = executionTimes.zipWithIndex.map((w, i) =>
    w.zipWithIndex.map((p, j) =>
      chocoModel.intVar(
        s"input_wc($i, $j)",
        0,
        dataTravelTime(i).sum,
        true
      )
    )
  )
  val durationsWrite = executionTimes.zipWithIndex.map((w, i) =>
    w.zipWithIndex.map((p, j) =>
      chocoModel.intVar(
        s"output_wc($i, $j)",
        0,
        dataTravelTime(i).sum,
        true
      )
    )
  )
  val durations = executionTimes.zipWithIndex.map((w, i) =>
    w.zipWithIndex.map((p, j) =>
      chocoModel.intVar(s"duration($i, $j)", 0, 
        durationsExec(i)(j).getUB() + 
        durationsFetch(i)(j).getUB() +
        durationsRead(i)(j).getUB() +
        durationsWrite(i)(j).getUB(),
        true  
      )
    )
  )

  private val commLoad = communicators
    .map(c => chocoModel.intVar(s"load($c)", 0, dataTravelTime.map(t => t(c)).sum, true))
    .toArray

  def postActive4StageDurationsConstraints(): Unit = {
    // deduced parameters
    // auxiliary local variables
    // scribe.debug(communicators.map(c => dataTravelTime.map(t => t(c)).sum).mkString(", "))
    // basic equality for durations
    for (i <- tasks; j <- processors) {
      // sum of all durations
      chocoModel.sum(Array(durationsFetch(i)(j), durationsFetch(i)(j), durationsRead(i)(j), durationsWrite(i)(j)), "=", durations(i)(j)).post()
      // for the execution times
      chocoModel.ifThenElse(
        chocoModel.arithm(taskExecution(i), "=", j),
        chocoModel.arithm(durationsExec(i)(j), "=", executionTimes(i)(j)),
        chocoModel.arithm(durationsExec(i)(j), "=", 0)
      )
    }
    // now for the communications
    // at least one path needs to be satisfied for isntruction fetching
    taskExecution.zipWithIndex.foreach((taskExec, t) =>
      taskMapping.zipWithIndex.foreach((taskMap, m) =>
        processors.foreach(p =>
          memories.foreach(mem =>
            val pathMatrix = allowedProc2MemoryDataPaths(p)(mem).map(path =>
              communicators.map(c => if (path.contains(c)) then 1 else -1).toArray
            )
            val pathTuples = Tuples(pathMatrix, true)
            pathTuples.setUniversalValue(-1)
            chocoModel.ifThen(
              taskExec.eq(p).and(taskMap.eq(mem)).decompose,
              // at least one of the paths must be taken
              chocoModel.table(taskCommunicationMapping(m).map(_.asInstanceOf[IntVar]), pathTuples)
            )
          )
        )
      )
    )
    // the same for data fetching or writting
    // they are symemtric in terms of constraints because the platform is assumed
    // in this module to have bidirectional links
    taskExecution.zipWithIndex.foreach((taskExec, t) =>
      dataMapping.zipWithIndex.foreach((dataMap, m) =>
        if (taskReadsData(t)(m) || taskWritesData(t)(m)) {
          processors.foreach(p =>
            memories.foreach(mem =>
              val pathMatrix = allowedProc2MemoryDataPaths(p)(mem).map(path =>
                communicators.map(c => if (path.contains(c)) then 1 else -1).toArray
              )
              val pathTuples = Tuples(pathMatrix, true)
              pathTuples.setUniversalValue(-1)
              chocoModel.ifThen(
                taskExec.eq(p).and(dataMap.eq(mem)).decompose,
                // at least one of the paths must be taken
                chocoModel.table(
                  dataCommunicationMapping(m).map(_.asInstanceOf[IntVar]),
                  pathTuples
                )
              )
            )
          )
        }
      )
    )
    // the load of each communicatior
    communicators.foreach(c =>
      chocoModel
        .scalar(
          taskCommunicationMapping.map(m => m(c).intVar) ++ dataCommunicationMapping.map(m =>
            m(c).intVar
          ),
          taskTravelTime.map(t => t(c)) ++ dataTravelTime.map(t => t(c)),
          "<=",
          commLoad(c)
        )
        .post
    )
    // simplistic and pessimistic time estimation for fetching
    durationsFetch.zipWithIndex.foreach((d, t) =>
      chocoModel
        .sum(
          taskCommunicationMapping(t).zipWithIndex.map((x, ce) => x.mul(commLoad(ce)).intVar),
          "<=",
          d
        )
        .post
    )
    durationsRead.zipWithIndex.foreach((d, t) =>
      chocoModel
        .sum(
          taskCommunicationMapping(t).zipWithIndex.map((x, ce) => x.mul(commLoad(ce)).intVar),
          "<=",
          d
        )
        .post
    )
    durationsWrite.zipWithIndex.foreach((d, t) =>
      chocoModel
        .sum(
          taskCommunicationMapping(t).zipWithIndex.map((x, ce) => x.mul(commLoad(ce)).intVar),
          "<=",
          d
        )
        .post
    )

  }

}

// very old stuff
// for the Fetch times
// sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
//     sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
//     sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((sk, k) =>
//         val tt = sourceForSyDeDecisionModel.schedHwModel.hardware
//         .maxTraversalTimePerBit(j)(k)
//         .divide(multiplier)
//         .multiply(memoryMultipler)
//         model.ifThenElse(
//         taskExecution(i).eq(k).and(taskMapping(i).eq(j)).decompose,
//         wcFetch(i)(k)
//             .ge(
//             tt.multiply(sourceForSyDeDecisionModel.taskModel.taskSizes(i)).doubleValue.ceil.toInt
//             )
//             .decompose,
//         wcFetch(i)(j).eq(0).decompose
//         )
//     )
//     })
// )
// for the Data times
/// channels
// sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
//     sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
//     sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
//         sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.foreach((c, ci) => {
//         val transferred = sourceForSyDeDecisionModel.taskModel.taskChannelReads(i)(ci).toInt
//         val tt = sourceForSyDeDecisionModel.schedHwModel.hardware
//             .maxTraversalTimePerBit(j)(k)
//             .divide(multiplier)
//             .multiply(memoryMultipler)
//             .multiply(transferred)
//         model.ifThenElse(
//             taskExecution(i).eq(k).and(dataBlockMapping(ci).eq(j)).decompose,
//             channelFetchTime(ci)(k)
//             .ge(
//                 tt.doubleValue.ceil.toInt * transferred
//             )
//             .decompose,
//             channelFetchTime(ci)(k).eq(0).decompose
//         )
//         })
//     )
//     })
// )
// sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
//     sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
//     model.ifThenElse(
//         taskExecution(i).eq(k).decompose,
//         wcInput(i)(k)
//         .ge(
//             model.sum(
//             s"input_task${i}",
//             sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex
//                 .filter((c, j) => sourceForSyDeDecisionModel.taskModel.taskChannelReads(i).contains(j))
//                 .map((c, j) => {
//                 channelFetchTime(j)(k)
//                 }): _*
//             )
//         )
//         .decompose,
//         wcInput(i)(k).eq(0).decompose
//     )
//     )
// )
// for the Write back times
// sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
//     sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
//     sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
//         sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.foreach((c, ci) => {
//         val transferred = sourceForSyDeDecisionModel.taskModel.taskChannelWrites(i)(ci).toInt
//         val tt = sourceForSyDeDecisionModel.schedHwModel.hardware
//             .maxTraversalTimePerBit(k)(j)
//             .divide(multiplier)
//             .multiply(memoryMultipler)
//             .multiply(transferred)
//         model.ifThenElse(
//             taskExecution(i).eq(k).and(dataBlockMapping(ci).eq(j)).decompose,
//             channelWriteTime(ci)(k)
//             .ge(
//                 tt.doubleValue.ceil.toInt * transferred
//             )
//             .decompose,
//             channelWriteTime(ci)(k).eq(0).decompose
//         )
//         })
//     )
//     })
// )
// sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
//     sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
//     model.ifThenElse(
//         taskExecution(i).eq(k).decompose,
//         wcOutput(i)(k)
//         .ge(
//             model.sum(
//             s"output_task${i}",
//             sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex
//                 .filter((c, j) => sourceForSyDeDecisionModel.taskModel.taskChannelWrites(i).contains(j))
//                 .map((c, j) => {
//                 channelWriteTime(j)(k)
//                 }): _*
//             )
//         )
//         .decompose,
//         wcOutput(i)(k).eq(0).decompose
//     )
//     )
// )

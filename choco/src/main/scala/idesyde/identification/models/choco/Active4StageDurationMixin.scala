package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar

trait Active4StageDurationMixin extends ChocoModelMixin {

  def executionTime: Array[Array[Int]]
  def taskTravelTime: Array[Array[Int]]
  def dataTravelTime: Array[Array[Int]]
  def allowedProc2MemoryDataPaths: Array[Array[Array[Array[Int]]]]
  def taskReadsData: Array[Array[Boolean]]
  def taskWritesData: Array[Array[Boolean]]

  def taskExecution: Array[IntVar]
  def taskMapping: Array[IntVar]
  def dataMapping: Array[IntVar]
  def taskCommunicationMapping: Array[Array[BoolVar]]
  def dataCommunicationMapping: Array[Array[BoolVar]]

  def durationsFetch: Array[IntVar]
  def durationsRead: Array[IntVar]
  def durationsExec: Array[IntVar]
  def durationsWrite: Array[IntVar]
  def durations: Array[IntVar]

  def postActive4StageDurationsConstraints(): Unit = {
    // deduced parameters
    val processors    = 0 until allowedProc2MemoryDataPaths.length
    val memories      = 0 until allowedProc2MemoryDataPaths.head.length
    val communicators = 0 until dataCommunicationMapping.head.length
    // auxiliary local variables
    val commLoad = communicators
      .map(c => chocoModel.intVar("load_" + c, 0, dataTravelTime.map(t => t(c)).sum))
      .toArray
    // posting constraints proper
    processors.map(processorIndex =>
      // sum of all durationss
      durations.zipWithIndex.foreach((dur, i) =>
        dur
          .eq(durationsFetch(i).add(durationsRead(i)).add(durationsExec(i)).add(durationsWrite(i)))
          .post
      )
      // for the execution times
      durationsExec.zipWithIndex.foreach((exe, i) =>
        chocoModel.ifThenElse(
          taskExecution(i).eq(processorIndex).decompose,
          exe.eq(executionTime(i)(processorIndex)).decompose,
          exe.eq(0).decompose
        )
      )
      // for the total times
      durations.zipWithIndex.foreach((dur, i) =>
        chocoModel.ifThenElse(
          taskExecution(i).eq(processorIndex).decompose,
          dur.eq(executionTime(i)(processorIndex)).decompose,
          dur.eq(0).decompose
        )
      )
    )
    // at least one path needs to be satisfied for isntruction fetching
    taskExecution.zipWithIndex.foreach((taskExec, t) =>
      taskMapping.zipWithIndex.foreach((taskMap, m) =>
          processors.foreach(p =>
            memories.foreach(mem =>
              val paths = allowedProc2MemoryDataPaths(p)(mem)
              chocoModel.ifThen(taskExec.eq(p).and(taskMap.eq(mem)).decompose, 
                // at least one of the paths must be taken  
                chocoModel.or(paths.map(path => chocoModel.and(taskCommunicationMapping(t):_*)):_*)
              )
            )
          )
      )
    )
    // the same for data fetching or writting
    // they are symemtric in terms of constraints because the platform is assumed
    // in this mixin to have bidirectional links
    taskExecution.zipWithIndex.foreach((taskExec, t) =>
      dataMapping.zipWithIndex.foreach((dataMap, m) =>
        if (taskReadsData(t)(m) || taskWritesData(t)(m)) {
          processors.foreach(p =>
            memories.foreach(mem =>
              val paths = allowedProc2MemoryDataPaths(p)(mem)
              chocoModel.ifThen(taskExec.eq(p).and(dataMap.eq(mem)).decompose, 
                // at least one of the paths must be taken  
                chocoModel.or(paths.map(path => chocoModel.and(dataCommunicationMapping(m):_*)):_*)
              )
            )
          )
        }
      )
    )
    // the load of each communicatior
    communicators.foreach(c =>
      chocoModel.scalar(
        taskCommunicationMapping.map(m => m(c).intVar) ++ dataCommunicationMapping.map(m => m(c).intVar),
        taskTravelTime.map(t => t(c)) ++ dataTravelTime.map(t => t(c)),
        "<=",
        commLoad(c)
      ).post
    )
    // simplistic and pessimistic time estimation for fetching
    durationsFetch.zipWithIndex.foreach((d, t) =>
      chocoModel.sum(taskCommunicationMapping(t).zipWithIndex.map((x, ce) => x.mul(commLoad(ce)).intVar), "<=", d).post
    )
    durationsRead.zipWithIndex.foreach((d, t) =>
      chocoModel.sum(taskCommunicationMapping(t).zipWithIndex.map((x, ce) => x.mul(commLoad(ce)).intVar), "<=", d).post
    )
    durationsWrite.zipWithIndex.foreach((d, t) =>
      chocoModel.sum(taskCommunicationMapping(t).zipWithIndex.map((x, ce) => x.mul(commLoad(ce)).intVar), "<=", d).post
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
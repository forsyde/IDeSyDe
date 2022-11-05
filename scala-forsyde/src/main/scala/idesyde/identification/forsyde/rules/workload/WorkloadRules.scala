package idesyde.identification.forsyde.rules.workload

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.workload.PeriodicDependentWorkload
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.impl.DataBlock
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus

object WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[PeriodicDependentWorkload] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { systemGraph =>
      var tasks: Array[Task]                        = Array.empty
      var dataBlocks: Array[DataBlock]              = Array.empty
      var periodicStimulus: Array[PeriodicStimulus] = Array.empty
      lazy val taskStimulusGraph =
        SimpleDirectedGraph[Task | PeriodicStimulus | Upsample | Downsample, DefaultEdge](
          () => tasks.head,
          () => DefaultEdge(),
          false
        )
      lazy val taskCommunicationGraph =
        SimpleDirectedGraph[CommunicatingTask | DataBlock, Long](
          () => CommunicatingTask.enforce(tasks.head),
          () => 0L,
          false
        )
      model.vertexSet.forEach(v =>
        Task
          .safeCast(v)
          .ifPresent(task =>
            tasks :+= task
            taskStimulusGraph.addVertex(task)
            CommunicatingTask.safeCast(task).ifPresent(ct => taskCommunicationGraph.addVertex(ct))
          )
        DataBlock
          .safeCast(v)
          .ifPresent(channel =>
            dataBlocks :+= channel
            taskCommunicationGraph.addVertex(channel)
          )
        PeriodicStimulus
          .safeCast(v)
          .ifPresent(stim =>
            periodicStimulus :+= stim
            taskStimulusGraph.addVertex(stim)
          )
        Upsample
          .safeCast(v)
          .ifPresent(upsample => taskStimulusGraph.addVertex(upsample))
        Downsample
          .safeCast(v)
          .ifPresent(downsample => taskStimulusGraph.addVertex(downsample))
      )
      // nothing can be done if there are no tasks
      // so we terminate early to avoid undefined analysis results
      scribe.debug(s"Num of tasks found in model: ${tasks.size}")
      if (tasks.isEmpty)
        return IdentificationResult(true, Option.empty)
      // now take a look which of the relevant vertexes are connected
      taskStimulusGraph.vertexSet.forEach(src =>
        taskStimulusGraph.vertexSet.forEach(dst =>
          if (model.hasConnection(src, dst)) then taskStimulusGraph.addEdge(src, dst)
        )
      )
      // do the task communication calculations
      taskCommunicationGraph.vertexSet.forEach(src =>
        taskCommunicationGraph.vertexSet.forEach(dst =>
          src match {
            case datablock: DataBlock =>
              dst match {
                case task: CommunicatingTask =>
                  val dataRead = model
                    .getAllEdges(datablock.getViewedVertex, task.getViewedVertex)
                    .stream
                    .mapToLong(e =>
                      e.getTargetPort
                        .map(inPort =>
                          task.getPortDataReadSize.getOrDefault(inPort, datablock.getMaxSizeInBits)
                        )
                        .orElse(0L)
                    )
                    .sum
                  taskCommunicationGraph.addEdge(datablock, task, dataRead)
                case _ =>
              }
            case _ =>
          }
          src match {
            case task: CommunicatingTask =>
              dst match {
                case datablock: DataBlock =>
                  val dataRead = model
                    .getAllEdges(task.getViewedVertex, datablock.getViewedVertex)
                    .stream
                    .mapToLong(e =>
                      e.getSourcePort
                        .map(outPort =>
                          task.getPortDataWrittenSize
                            .getOrDefault(outPort, datablock.getMaxSizeInBits)
                        )
                        .orElse(0L)
                    )
                    .sum
                  taskCommunicationGraph.addEdge(task, datablock, dataRead)
                case _ =>
              }
            case _ =>
          }
        )
      )
      // check if every task has a periodic stimulus
      val connectivityInspector = ConnectivityInspector(taskStimulusGraph)
      val allTasksAreStimulated = tasks.forall(task =>
        periodicStimulus.exists(stim => connectivityInspector.pathExists(stim, task))
      )
      scribe.debug(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
      if (allTasksAreStimulated) then
        new IdentificationResult(
          true,
          Some(
            ForSyDePeriodicWorkload(
              tasks,
              periodicStimulus,
              dataBlocks,
              tasks.map(task =>
                LoopingTask
                  .safeCast(task)
                  .map(lt => lt.getLoopSequencePort(model).asScala.toArray)
                  .orElse(Array.empty)
              ),
              taskStimulusGraph,
              taskCommunicationGraph
            )
          )
        )
      else new IdentificationResult(true, Option.empty)
    }
  }
}

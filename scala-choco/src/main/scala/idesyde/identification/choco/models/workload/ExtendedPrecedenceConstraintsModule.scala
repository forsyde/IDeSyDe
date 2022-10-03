package idesyde.identification.choco.models.workload

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.Graph
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.Model

class ExtendedPrecedenceConstraintsModule(
  val chocoModel: Model,
  val taskExecution: Array[IntVar],
  val responseTimes: Array[IntVar],
  val blockingTimes: Array[IntVar],
  val canBeFollowedBy: Array[Array[Boolean]]
) extends ChocoModelMixin() {

  private val processors = (0 until taskExecution.map(v => v.getUB()).max).toArray

  def postInterProcessorBlocking(): Unit = {
    canBeFollowedBy.zipWithIndex.foreach((arr, src) =>
      arr.zipWithIndex
        .filter((possible, _) => possible)
        .foreach((_, dst) =>
          processors.foreach(processorsIdx =>
            chocoModel.ifThen(
              // if the mappings differ in at least one processor
              taskExecution(dst).eq(processorsIdx)
                .and(taskExecution(src).ne(processorsIdx))
                .decompose,
              blockingTimes(dst).ge(responseTimes(src)).decompose
            )
          )
        )
    )
    // canBeFollowedBy.edgeSet.forEach(e => {
    //     val src = canBeFollowedBy.getEdgeSource(e)
    //     val dst = canBeFollowedBy.getEdgeTarget(e)
    //     //scribe.debug(s"dst ${dst} and src ${src}")
    //     chocoModel.ifThen(
    //       taskExecution(dst).ne(taskExecution(src)).decompose,
    //       blockingTimes(dst).ge(responseTimes(src)).decompose
    //     )
    // })
  }

}

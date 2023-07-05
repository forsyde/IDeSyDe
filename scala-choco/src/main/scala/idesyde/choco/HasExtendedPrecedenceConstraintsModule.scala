package idesyde.choco

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.Graph
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.Model

trait HasExtendedPrecedenceConstraints {

  def postInterProcessorJitters(
      chocoModel: Model,
      taskExecution: Array[IntVar],
      responseTimes: Array[IntVar],
      releaseJitters: Array[IntVar],
      canBeFollowedBy: Array[Array[Boolean]]
  ): Unit = {
    canBeFollowedBy.zipWithIndex.foreach((arr, src) =>
      arr.zipWithIndex
        .filter((possible, _) => possible)
        .foreach((_, dst) =>
          chocoModel.ifThen(
            // if the mappings differ in at least one processor
            taskExecution(dst).ne(taskExecution(src)).decompose,
            releaseJitters(dst).ge(responseTimes(src)).decompose
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

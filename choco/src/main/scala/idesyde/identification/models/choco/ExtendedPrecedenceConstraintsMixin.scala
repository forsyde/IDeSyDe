package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.Graph

trait ExtendedPrecedenceConstraintsMixin extends ChocoModelMixin {

  def taskExecution: Array[IntVar]
  def responseTimes: Array[IntVar]
  def blockingTimes: Array[IntVar]
  def canBeFollowedBy: Array[Array[Boolean]]

  def postInterProcessorBlocking(): Unit = {
    canBeFollowedBy.zipWithIndex.foreach((arr, src) =>
      arr.zipWithIndex.filter((possible, _) => possible).foreach((_, dst) =>
        chocoModel.ifThen(
          taskExecution(dst).ne(taskExecution(src)).decompose,
          blockingTimes(dst).ge(responseTimes(src)).decompose
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

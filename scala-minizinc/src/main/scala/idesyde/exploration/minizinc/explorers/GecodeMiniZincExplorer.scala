package idesyde.exploration.minizinc.explorers

import scala.sys.process._
import java.time.Duration
import idesyde.exploration.minizinc.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.identification.minizinc.MiniZincData
import idesyde.core.DecisionModel
import idesyde.identification.common.StandardDecisionModel

final case class GecodeMiniZincExplorer() extends SimpleMiniZincCPExplorer {

  def canExplore(decisionModel: DecisionModel): Boolean = false
  // "minizinc --solvers".!!.contains("org.gecode.gecode")

  override def explore[T <: DecisionModel](
      decisionModel: T,
      explorationTimeOutInSecs: Long
  ): LazyList[T] = LazyList.empty

  def uniqueIdentifier: String = "GecodeMiniZincExplorer"
}

object GecodeMiniZincExplorer:

  val extraHeaderReactorMinusAppMapAndSchedMzn: String = "include \"gecode.mzn\";\n"

  val extraInstReactorMinusAppMapAndSchedMzn: String =
    """
    solve
    :: warm_start(reactionExecution, [arg_min(p in ProcessingElems where reactionCanBeExecuted[r, p]) (reactionWcet[r, p]) | r in Reactions])
    :: restart_luby(length(Reactions) * length(ProcessingElems))
    :: relax_and_reconstruct(reactionExecution, 20)
    minimize goal;
    """

end GecodeMiniZincExplorer

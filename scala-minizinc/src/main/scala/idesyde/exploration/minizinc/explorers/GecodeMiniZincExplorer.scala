package idesyde.exploration.minizinc.explorers

import idesyde.identification.minizinc.interfaces.MiniZincForSyDeDecisionModel

import scala.sys.process._
import java.time.Duration
import idesyde.exploration.minizinc.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeSystemGraph
import java.nio.file.Files
import idesyde.identification.forsyde.ForSyDeDecisionModel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.identification.minizinc.interfaces.MiniZincData
import forsyde.io.java.core.EdgeTrait
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDecisionModel

final case class GecodeMiniZincExplorer()
    extends SimpleMiniZincCPExplorer:

  override def canExploreForSyDe(decisionModel: ForSyDeDecisionModel): Boolean =
    "minizinc --solvers".!!.contains("org.gecode.gecode")

  def exploreForSyDe(decisionModel: ForSyDeDecisionModel, explorationTimeOutInSecs: Long = 0L) =
    decisionModel match
      case _ => LazyList.empty

end GecodeMiniZincExplorer

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

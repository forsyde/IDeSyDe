package idesyde.exploration.explorers

import idesyde.identification.interfaces.MiniZincForSyDeDecisionModel

import scala.sys.process._
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import java.time.Duration
import idesyde.exploration.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeSystemGraph
import java.nio.file.Files
import idesyde.identification.ForSyDeDecisionModel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.identification.interfaces.MiniZincData
import forsyde.io.java.core.EdgeTrait

final case class GecodeMiniZincExplorer() extends SimpleMiniZincCPExplorer with ReactorMinusDSEMznMerger:

  override def canExplore(ForSyDeDecisionModel: ForSyDeDecisionModel): Boolean =
    super.canExplore(ForSyDeDecisionModel) &&
      "minizinc --solvers".!!.contains("org.gecode.gecode")

  def estimateTimeUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Duration =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m.sourceModel
        Duration.ofSeconds(
          nonMznForSyDeDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.channels.size
        )
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Duration =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m.sourceModel
        Duration.ofHours(
          nonMznForSyDeDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.channels.size * nonMznForSyDeDecisionModel.platform.coveredVertexes.size 
        )
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Long =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m.sourceModel
        128 * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Long =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m
        50 * estimateMemoryUntilFeasibility(ForSyDeDecisionModel)
      case _ => 0

  def explore(ForSyDeDecisionModel: ForSyDeDecisionModel)(using ExecutionContext) =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val results = explorationSolve(m, "gecode", 
        extraHeader = GecodeMiniZincExplorer.extraHeaderReactorMinusAppMapAndSchedMzn,
        extraInstruction = GecodeMiniZincExplorer.extraInstReactorMinusAppMapAndSchedMzn)
        results.map(result => mergeResults(m, result))
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
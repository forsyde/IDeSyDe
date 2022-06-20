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
import idesyde.exploration.explorers.ChuffedMiniZincExplorer
import idesyde.identification.DecisionModel

final case class ChuffedMiniZincExplorer()
    extends SimpleMiniZincCPExplorer
    with ReactorMinusDSEMznMerger:

  override def canExplore(decisionModel: DecisionModel): Boolean =
    decisionModel match {
      case m: ForSyDeDecisionModel =>
        "minizinc --solvers".!!.contains("org.chuffed.chuffed")
      case _ => false
    }

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m.sourceModel
        Duration.ofSeconds(
          nonMznForSyDeDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.channels.size * 5
        )
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m.sourceModel
        Duration.ofMinutes(
          nonMznForSyDeDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.channels.size * nonMznForSyDeDecisionModel.platform.coveredVertexes.size
        )
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m.sourceModel
        128 * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznForSyDeDecisionModel.reactorMinus.jobGraph.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznForSyDeDecisionModel = m
        50 * estimateMemoryUntilFeasibility(decisionModel)
      case _ => 0

  def explore(decisionModel: DecisionModel)(using ExecutionContext) =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        explorationSolve(
          m,
          "chuffed",
          callExtraFlags = List("-f"),
          extraHeader = ChuffedMiniZincExplorer.extraHeaderReactorMinusAppMapAndSchedMzn,
          extraInstruction = ChuffedMiniZincExplorer.extraInstReactorMinusAppMapAndSchedMzn
        ).map(result => mergeResults(m, result))
      case _ => LazyList.empty

end ChuffedMiniZincExplorer

object ChuffedMiniZincExplorer:

  val extraHeaderReactorMinusAppMapAndSchedMzn: String = "include \"chuffed.mzn\";\n"

  val extraInstReactorMinusAppMapAndSchedMzn: String =
    """
    solve
    :: restart_luby(length(Reactions) * length(ProcessingElems))
    minimize goal;
    """

end ChuffedMiniZincExplorer

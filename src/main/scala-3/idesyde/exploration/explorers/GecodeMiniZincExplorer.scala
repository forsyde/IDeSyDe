package idesyde.exploration.explorers

import idesyde.identification.interfaces.MiniZincDecisionModel

import scala.sys.process._
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import java.time.Duration
import idesyde.exploration.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel
import java.nio.file.Files
import idesyde.identification.DecisionModel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.identification.interfaces.MiniZincData

final case class GecodeMiniZincExplorer() extends SimpleMiniZincCPExplorer:

  override def canExplore(decisionModel: DecisionModel): Boolean =
    super.canExplore(decisionModel) &&
      "minizinc --solvers".!!.contains("org.gecode.gecode")

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        Duration.ofSeconds(
          nonMznDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznDecisionModel.reactorMinus.jobGraph.channels.size
        )
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        Duration.ofMinutes(
          nonMznDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznDecisionModel.reactorMinus.jobGraph.channels.size * nonMznDecisionModel.platform.coveredVertexes.size 
        )
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        128 * nonMznDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznDecisionModel.reactorMinus.jobGraph.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m
        50 * estimateMemoryUntilFeasibility(decisionModel)
      case _ => 0

  def explore(decisionModel: DecisionModel)(using ExecutionContext) =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val resString = explorationSolve(m, "gecode", 
        extraHeader = GecodeMiniZincExplorer.extraHeaderReactorMinusAppMapAndSchedMzn,
        extraInstruction = GecodeMiniZincExplorer.extraInstReactorMinusAppMapAndSchedMzn)
        resString.foreach(s => 
          scribe.debug(s)
          scribe.debug(s.split(";").toString)
          s.split(";")
           .filter(_.contains("="))
           .map(line =>
            line.split(" = ") match
              case Array(name, data, _*) =>
                name -> MiniZincData.fromResultString(data)
          )
          // ForSyDeModel()
        )
        LazyList.empty
      case _ => LazyList.empty

end GecodeMiniZincExplorer

object GecodeMiniZincExplorer:

  val extraHeaderReactorMinusAppMapAndSchedMzn: String = "include \"gecode.mzn\";\n"

  val extraInstReactorMinusAppMapAndSchedMzn: String =
    """
    solve
    :: warm_start(reactionExecution, [arg_min(p in ProcessingElems where reactionCanBeExecuted[r, p]) (reactionWcet[r, p]) | r in Reactions])
    :: seq_search([
      int_search(reactionExecution, input_order, indomain_min),
      int_search(reactionResponseTime,  smallest, indomain_min),
      int_search(reactionOffsetTime,  smallest, indomain_min),
      int_search(reactionRRSlices, first_fail, indomain_max),
      int_search(reactionUtilization, first_fail, indomain_min),
      int_search(latenciesVariation, first_fail, indomain_min)
    ])
    :: restart_luby(length(Reactions) * length(ProcessingElems))
    :: relax_and_reconstruct(reactionExecution, 20)
    minimize goal;
    """

end GecodeMiniZincExplorer
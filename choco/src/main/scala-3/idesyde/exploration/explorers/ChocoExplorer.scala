package idesyde.exploration.explorers

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.interfaces.ChocoCPForSyDeDecisionModel
import java.time.Duration
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.exploration.interfaces.Explorer
import org.chocosolver.solver.objective.ParetoMaximizer

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import org.chocosolver.solver.search.limits.SolutionCounter
import org.chocosolver.solver.Solution

class ChocoExplorer() extends Explorer:

  def canExplore(ForSyDeDecisionModel: ForSyDeDecisionModel): Boolean = ForSyDeDecisionModel match
    case chocoModel: ChocoCPForSyDeDecisionModel => true
    case _                                => false

  def estimateMemoryUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Long = ForSyDeDecisionModel match
    case chocoModel: ChocoCPForSyDeDecisionModel =>
      chocoModel.chocoModel.getVars.size * 10
    case _ => Long.MaxValue

  def estimateMemoryUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Long = ForSyDeDecisionModel match
    case chocoModel: ChocoCPForSyDeDecisionModel =>
      chocoModel.chocoModel.getVars.size * 1000
    case _ => Long.MaxValue

  def estimateTimeUntilFeasibility(
      ForSyDeDecisionModel: ForSyDeDecisionModel
  ): java.time.Duration = ForSyDeDecisionModel match
    case chocoModel: ChocoCPForSyDeDecisionModel =>
      Duration.ofMinutes(chocoModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def estimateTimeUntilOptimality(
      ForSyDeDecisionModel: ForSyDeDecisionModel
  ): java.time.Duration = ForSyDeDecisionModel match
    case chocoModel: ChocoCPForSyDeDecisionModel =>
      Duration.ofHours(chocoModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def explore(ForSyDeDecisionModel: ForSyDeDecisionModel)(using
      ExecutionContext
  ): LazyList[ForSyDeSystemGraph] = ForSyDeDecisionModel match
    case chocoCpModel: ChocoCPForSyDeDecisionModel =>
      val model           = chocoCpModel.chocoModel
      val solver          = model.getSolver
      val paretoMaximizer = ParetoMaximizer(chocoCpModel.modelObjectives)
      solver.plugMonitor(paretoMaximizer)
      solver.setLearningSignedClauses
      solver.setNoGoodRecordingFromRestarts
      solver.setRestartOnSolutions
      solver.addStopCriterion(SolutionCounter(model, 100L))
      if (!chocoCpModel.strategies.isEmpty) then solver.setSearch(chocoCpModel.strategies: _*)
      LazyList
        .continually(solver.solve)
        .takeWhile(feasible => feasible)
        .filter(feasible => feasible)
        .flatMap(feasible => {
          //scribe.debug(s"pareto size: ${paretoMaximizer.getParetoFront.size}")
          paretoMaximizer.getParetoFront.asScala
        })
        .map(paretoSolutions => {
          chocoCpModel.rebuildFromChocoOutput(paretoSolutions)
        })
    case _ => LazyList.empty

end ChocoExplorer

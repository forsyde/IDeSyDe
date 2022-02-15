package idesyde.exploration.explorers

import idesyde.identification.DecisionModel
import idesyde.identification.interfaces.ChocoCPDecisionModel
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

  def canExplore(decisionModel: DecisionModel): Boolean = decisionModel match
    case chocoModel: ChocoCPDecisionModel => true
    case _                                => false

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long = decisionModel match
    case chocoModel: ChocoCPDecisionModel =>
      chocoModel.chocoModel.getVars.size * 10
    case _ => Long.MaxValue

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long = decisionModel match
    case chocoModel: ChocoCPDecisionModel =>
      chocoModel.chocoModel.getVars.size * 1000
    case _ => Long.MaxValue

  def estimateTimeUntilFeasibility(
      decisionModel: DecisionModel
  ): java.time.Duration = decisionModel match
    case chocoModel: ChocoCPDecisionModel =>
      Duration.ofMinutes(chocoModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def estimateTimeUntilOptimality(
      decisionModel: DecisionModel
  ): java.time.Duration = decisionModel match
    case chocoModel: ChocoCPDecisionModel =>
      Duration.ofHours(chocoModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def explore(decisionModel: DecisionModel)(using
      ExecutionContext
  ): LazyList[ForSyDeSystemGraph] = decisionModel match
    case chocoCpModel: ChocoCPDecisionModel =>
      val model           = chocoCpModel.chocoModel
      val solver          = model.getSolver
      val solution        = Solution(model)
      val paretoMaximizer = ParetoMaximizer(chocoCpModel.modelObjectives)
      solver.plugMonitor(paretoMaximizer)
      solver.addStopCriterion(SolutionCounter(model, 100L))
      if (!chocoCpModel.searchStrategies.isEmpty) then solver.setSearch(chocoCpModel.searchStrategies:_*)
      LazyList
        .continually(solver.solve)
        .takeWhile(feasible => feasible || !solver.isStopCriterionMet)
        .filter(feasible => feasible)
        .flatMap(feasible => paretoMaximizer.getParetoFront.asScala)
        .map(feasible => {
          chocoCpModel.rebuildFromChocoOutput(solution.record)
        })
    case _ => LazyList.empty

end ChocoExplorer

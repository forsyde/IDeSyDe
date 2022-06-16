package idesyde.exploration.interfaces

import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Duration
import idesyde.exploration.api.ExplorationCriteria
import idesyde.identification.DecisionModel

trait Explorer {

  type BaseModel

  def explore(decisionModel: DecisionModel)(using
      ExecutionContext
  ): LazyList[BaseModel]

  def canExplore(decisionModel: DecisionModel): Boolean

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long

  def dominates(
      o: Explorer,
      m: DecisionModel,
      criteria: Set[ExplorationCriteria] = Set()
  ): Boolean =
    val c =
      (if (criteria.contains(ExplorationCriteria.TimeUntilFeasibility))
         estimateTimeUntilFeasibility(m).compareTo(o.estimateTimeUntilFeasibility(m)) < 0
       else
         true) &&
        (if (criteria.contains(ExplorationCriteria.TimeUntilOptimality))
           estimateTimeUntilOptimality(m).compareTo(o.estimateTimeUntilOptimality(m)) < 0
         else
           true) &&
        (if (criteria.contains(ExplorationCriteria.MemoryUntilFeasibility))
           estimateMemoryUntilFeasibility(m) < o.estimateMemoryUntilFeasibility(m)
         else
           true) &&
        (if (criteria.contains(ExplorationCriteria.MemoryUntilOptimality))
           estimateMemoryUntilOptimality(m) < o.estimateMemoryUntilOptimality(m)
         else
           true)
    c && canExplore(m) && o.canExplore(m)

}

package idesyde.exploration.interfaces

import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Duration
import idesyde.exploration.api.ExplorationCriteria

trait Explorer {

  def explore(ForSyDeDecisionModel: ForSyDeDecisionModel)(using ExecutionContext): LazyList[ForSyDeSystemGraph]

  def canExplore(ForSyDeDecisionModel: ForSyDeDecisionModel): Boolean

  def estimateTimeUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Duration

  def estimateTimeUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Duration

  def estimateMemoryUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Long

  def estimateMemoryUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Long

  def dominates(
      o: Explorer,
      m: ForSyDeDecisionModel,
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

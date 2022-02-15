package idesyde.exploration.explorers

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.solver.search.strategy.decision.Decision
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory

class SimpleListSchedulingDecisionStrategy(
    val schedulers: Array[Int],
    val taskExecutions: Array[IntVar],
    val responseTimes: Array[IntVar]
) extends AbstractStrategy[IntVar]((taskExecutions ++ responseTimes): _*) {

  val pool = PoolManager[IntDecision]()

  def getDecision(): Decision[IntVar] = {
    val d = Option(pool.getE()).getOrElse(IntDecision(pool))
    val maxRs = schedulers.zipWithIndex.map((s, j) => {
      responseTimes.zipWithIndex
        .map((t, i) => {
          if (taskExecutions(i).contains(j)) responseTimes(i).getUB
          else 0
        })
        .max
    })
    taskExecutions
      .find(!_.isInstantiated)
      .map(next => {
        val minSched = maxRs.zipWithIndex.minBy((r, i) => r)._2
        d.set(next, minSched, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

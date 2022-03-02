package idesyde.exploration.explorers

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.solver.search.strategy.decision.Decision
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import org.apache.commons.math3.fraction.BigFraction

class SimpleWorkloadBalancingDecisionStrategy(
    val schedulers: Array[Int],
    val periods: Array[BigFraction],
    val taskExecutions: Array[IntVar],
    val durations: Array[Array[IntVar]]
)(using Numeric[BigFraction]) extends AbstractStrategy[IntVar]((taskExecutions ++ durations.flatten): _*) {

  val pool = PoolManager[IntDecision]()

  def getDecision(): Decision[IntVar] = {
    val d = Option(pool.getE()).getOrElse(IntDecision(pool))
    val utilizations = schedulers.map(j => {
      durations.zipWithIndex
        .map((ws, i) => {
          if (taskExecutions(i).isInstantiatedTo(j)) periods(i).reciprocal.multiply(ws(j).getLB)
          else BigFraction.ZERO
        })
        .sum
    })
    taskExecutions
      .filterNot(_.isInstantiated)
      .zipWithIndex
      .flatMap((t, i) => 
        schedulers.map(j => (t, i, j, utilizations(j).add(periods(i).reciprocal.multiply(durations(i)(j).getLB))))
      )
      .filter((t, i, j, w) => taskExecutions(i).contains(j) && w.compareTo(BigFraction.ONE) <= 0)
      .minByOption((t, i, j, w) => w)
      .map((t, i, j, w) => {
        //scribe.debug(s"choosing ${j} for ${i} due to ${w}")
        d.set(t, j, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

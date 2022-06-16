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
    val utilizations: Array[IntVar],
    val durations: Array[IntVar]
)(using Numeric[BigFraction]) extends AbstractStrategy[IntVar]((taskExecutions): _*) {

  val pool = PoolManager[IntDecision]()

  def getDecision(): Decision[IntVar] = {
    val d = Option(pool.getE()).getOrElse(IntDecision(pool))
    // val utilizations = schedulers.map(j => {
    //   durations.zipWithIndex
    //     .map((ws, i) => {
    //       if (taskExecutions(i).isInstantiatedTo(j)) periods(i).reciprocal.multiply(ws(j).getLB)
    //       else BigFraction.ZERO
    //     })
    //     .sum
    // })
    // scribe.debug(s"utilizations: ${utilizations.map(_.getLB).mkString("[", ",", "]")}")
    taskExecutions
      .zipWithIndex
      .filterNot(_._1.isInstantiated)
      .flatMap((t, i) => 
        schedulers.map(j => (t, i, j, periods(i).reciprocal.multiply(100 * durations(i).getLB).add(utilizations(j).getLB).intValue))
      )
      .filter((t, i, j, w) => t.contains(j))
      .minByOption((t, i, j, w) => if (w == 0) then 100 else w)
      .map((t, i, j, w) => {
        // scribe.debug(s"choosing ${i} -> ${j} due to ${w}")
        // scribe.debug(s"range for ${i}: ${taskExecutions(i).getLB} : ${taskExecutions(i).getUB}")
        d.set(t, j, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

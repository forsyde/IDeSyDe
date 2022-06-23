package idesyde.exploration.explorers

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.solver.search.strategy.decision.Decision
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import org.apache.commons.math3.fraction.BigFraction
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.util.ESat

class SimpleWorkloadBalancingDecisionStrategy(
    val schedulers: Array[Int],
    val periods: Array[BigFraction],
    val taskExecutions: Array[Array[BoolVar]],
    val utilizations: Array[IntVar],
    val durations: Array[Array[IntVar]]
)(using Numeric[BigFraction])
    extends AbstractStrategy[IntVar]((taskExecutions.flatten): _*) {

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
    taskExecutions.zipWithIndex
      // keep only tasks that can still be mapped
      .filterNot((t, i) => t.forall(_.isInstantiated))
      // choose hte one that causes the least utilization
      .flatMap((t, i) =>
        schedulers.map(j =>
          (
            t,
            i,
            j,
            periods(i).reciprocal
              .multiply(100 * durations(i)(j).getLB)
              .add(utilizations(j).getLB)
              .intValue
          )
        )
      )
      // filter tasks that cannot be mapped into j
      .filterNot((t, i, j, w) => t(j).getBooleanValue.equals(ESat.FALSE))
      .minByOption((t, i, j, w) => if (w == 0) then 100 else w)
      .map((t, i, j, w) => {
        // scribe.debug(s"choosing ${i} -> ${j} due to ${w}")
        // scribe.debug(s"range for ${i}: ${taskExecutions(i).getLB} : ${taskExecutions(i).getUB}")
        d.set(t(j), 1, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

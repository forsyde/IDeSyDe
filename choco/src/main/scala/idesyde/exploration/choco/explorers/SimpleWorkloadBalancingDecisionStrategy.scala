package idesyde.exploration.explorers

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.solver.search.strategy.decision.Decision
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.util.ESat
import spire.math.*

class SimpleWorkloadBalancingDecisionStrategy(
    val schedulers: Array[Int],
    val periods: Array[Rational],
    val taskExecutions: Array[Array[BoolVar]],
    val utilizations: Array[IntVar],
    val durations: Array[Array[IntVar]]
)(using Numeric[Rational])
    extends AbstractStrategy[IntVar]((taskExecutions.flatten): _*) {

  val pool = PoolManager[IntDecision]()

  def getDecision(): Decision[IntVar] = {
    val d = Option(pool.getE()).getOrElse(IntDecision(pool))
    // val utilizations = schedulers.map(j => {
    //   durations.zipWithIndex
    //     .map((ws, i) => {
    //       if (taskExecutions(i).isInstantiatedTo(j)) periods(i).reciprocal.multiply(ws(j).getLB)
    //       else Rational.zero
    //     })
    //     .sum
    // })
    // scribe.debug(s"utilizations: ${utilizations.map(_.getLB).mkString("[", ",", "]")}")
    taskExecutions.zipWithIndex
      // keep only tasks that can still be mapped
      // .filterNot((t, i) => t.forall(_.isInstantiated))
      // choose the one that causes the least utilization
      // penalized by the number of tasks already scheduled
      .flatMap((t, i) =>
        schedulers.map(j =>
          (
            t(j),
            i,
            j,
            // the count is multiplied by 100 so that it has priority over U
            t.count(_.isInstantiatedTo(1)),
            utilizations(j).getLB,
            (periods(i).reciprocal
              * (100 * durations(i)(j).getLB)).intValue
          )
        )
      )
      // filter tasks that cannot be mapped into j and are still not mapped
      .filter((t, i, j, n, u, w) => !t.isInstantiated && t.contains(1))
      // order lexographically by min numTasks (n), then max utilization (u) and min util. increment (w)
      .minByOption((t, i, j, n, u, w) => (n, 100 - u, w))
      .map((t, i, j, n, u, w) => {
        // scribe.debug(s"choosing ${i} -> ${j} due to ${w}")
        // scribe.debug(s"range for ${i}: ${taskExecutions(i).getLB} : ${taskExecutions(i).getUB}")
        d.set(t, 1, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

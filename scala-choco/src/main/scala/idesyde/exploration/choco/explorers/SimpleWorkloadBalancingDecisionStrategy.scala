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
    val taskExecutions: Array[IntVar],
    val utilizations: Array[IntVar],
    val durations: Array[IntVar],
    val wcets: Array[Array[Int]]
)(using Numeric[Rational])
    extends AbstractStrategy[IntVar](taskExecutions: _*) {

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
            t,
            i,
            j,
            utilizations(j).getLB() + Math.max(durations(i).getLB(), wcets(i)(j)) / periods(
              i
            ) + utilizations.zipWithIndex
              .filter((_, jj) => jj != j)
              .map((ujj, _) => ujj.getLB())
              .sum
          )
        )
      )
      // filter tasks that cannot be mapped into j and are still not mapped
      .filter((t, i, j, u) => !t.isInstantiated() && t.contains(j))
      // order lexographically by max utilization (u), min numTasks (n), min util. increment (w)
      .minByOption((t, i, j, u) => u)
      .map((t, i, j, u) => {
        // println(s"choosing ${i} -> ${j} due to ($n, $u)")
        // scribe.debug(s"range for ${i}: ${taskExecutions(i).getLB} : ${taskExecutions(i).getUB}")
        d.set(t, j, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

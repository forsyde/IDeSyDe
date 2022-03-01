package idesyde.exploration.explorers

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.solver.search.strategy.decision.Decision
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory

class SimpleWorkloadBalancingDecisionStrategy(
    val schedulers: Array[Int],
    val periods: Array[Int],
    val taskExecutions: Array[IntVar],
    val wcets: Array[Array[IntVar]]
) extends AbstractStrategy[IntVar]((taskExecutions ++ wcets.flatten): _*) {

  val pool = PoolManager[IntDecision]()

  def getDecision(): Decision[IntVar] = {
    val d = Option(pool.getE()).getOrElse(IntDecision(pool))
    val utilizations = schedulers.map(j => {
      wcets.zipWithIndex
        .map((ws, i) => {
          if (taskExecutions(i).isInstantiatedTo(j) && ws(j).getLB > -1) ws(j).getUB / periods(i)
          else 0
        })
        .sum
    })
    taskExecutions
      .filterNot(_.isInstantiated)
      .zipWithIndex
      .flatMap((t, i) => 
        schedulers.map(j => (t, i, j, wcets(i)(j).getUB / periods(i) + utilizations(j)))
      )
      .minByOption((t, i, j, w) => w)
      .map((t, i, j, w) => {
        //scribe.debug(s"choosing ${j} for ${i} due to ${w}")
        d.set(t, j, DecisionOperatorFactory.makeIntEq)
        d
      })
      .getOrElse(null)
  }

}

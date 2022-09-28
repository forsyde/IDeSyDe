package idesyde.exploration.choco.explorers

import org.chocosolver.solver.search.loop.monitors.IMonitorSolution
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import scala.collection.mutable.Buffer
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.util.ESat

class ParetoMinimizationBrancher(val objectives: Array[IntVar])
    extends Propagator[IntVar](objectives: _*)
    with IMonitorSolution {

  val dominantObjValues   = objectives.map(_.getUB())
  val paretoFront         = Buffer[Solution]()
  var currentMandatoryMin = -1

  override def propagate(evtmask: Int): Unit = {
    objectives.zipWithIndex.foreach((o, i) => o.updateUpperBound(dominantObjValues(i), this))
    if (currentMandatoryMin > -1) {
      objectives(currentMandatoryMin).updateUpperBound(
        dominantObjValues(currentMandatoryMin) - 1,
        this
      )
    }
  }

  override def isEntailed(): ESat = {
    if (objectives.zipWithIndex.forall((o, i) => o.getUB() <= dominantObjValues(i))) ESat.TRUE
    else if (objectives.zipWithIndex.exists((o, i) => o.getLB() > dominantObjValues(i))) ESat.FALSE
    else ESat.UNDEFINED
  }

  def onSolution(): Unit = {
    // check if it is a fully dominant solution
    if (objectives.zipWithIndex.forall((o, i) => o.getValue() < dominantObjValues(i))) {
      for ((o, i) <- objectives.zipWithIndex) {
        dominantObjValues(i) = o.getValue()
      }
      paretoFront.clear()
      currentMandatoryMin = -1
    } else {
      currentMandatoryMin = (currentMandatoryMin + 1) % dominantObjValues.length
    }
    paretoFront += model.getSolver().defaultSolution().record()
  }
}

package idesyde.exploration.choco.explorers

import org.chocosolver.solver.search.loop.monitors.IMonitorSolution
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import scala.collection.mutable.Buffer
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.util.ESat
import org.chocosolver.solver.constraints.PropagatorPriority
import idesyde.utils.CoreUtils.wfor

class ParetoMinimizationBrancher(val objectives: Array[IntVar])
    extends Propagator[IntVar](objectives, PropagatorPriority.BINARY, false)
    with IMonitorSolution {

  private val numObjs      = objectives.size
  private val minObjValues = objectives.map(_.getLB())
  // val dominantObjValues   = objectives.map(_.getUB())
  val paretoFront    = Buffer[Solution]()
  val paretoObjFront = Buffer[Array[Int]]()

  override def propagate(evtmask: Int): Unit = {
    // iterate though the current pareto front
    wfor(0, _ < paretoFront.size, _ + 1) { soli =>
      var count        = numObjs
      var lastUnderIdx = -1
      var lastUnderVal = -1
      wfor(0, _ < numObjs, _ + 1) { j =>
        if (paretoObjFront(soli)(j) <= objectives(j).getLB()) {
          count -= 1
        } else if (objectives(j).getLB() < paretoObjFront(soli)(j)) {
          lastUnderIdx = j
          lastUnderVal = paretoObjFront(soli)(j)
        }
      }
      if (count == 1) {
        objectives(lastUnderIdx).updateUpperBound(lastUnderVal - 1, this)
      } else if (count == 0) {
        fails()
      }
    }
  }

  override def isEntailed(): ESat = {
    if (
      paretoObjFront.isEmpty || paretoObjFront.zipWithIndex
        .forall((s, i) => s.zipWithIndex.exists((o, j) => objectives(j).getUB() < o))
    ) ESat.TRUE
    else if (
      paretoObjFront.zipWithIndex
        .exists((s, i) => s.zipWithIndex.forall((o, j) => o <= objectives(j).getLB()))
    ) ESat.FALSE
    else ESat.UNDEFINED
  }

  def onSolution(): Unit = {
    val solObjs = objectives.map(_.getValue())
    // check if it is a fully dominant solution
    if (
      paretoObjFront.zipWithIndex
        .forall((s, i) => s.zipWithIndex.forall((o, j) => objectives(j).getValue() < o))
    ) {
      // println("New frontier")
      paretoFront.clear()
      paretoObjFront.clear()
    }
    // println("Same frontier")
    paretoFront += model.getSolver().defaultSolution().record()
    paretoObjFront += solObjs
  }
}

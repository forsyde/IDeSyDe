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
import java.time.LocalDateTime

class ParetoMinimizationBrancher(val objectives: Array[IntVar])
    extends Propagator[IntVar](objectives, PropagatorPriority.BINARY, false)
    with IMonitorSolution {

  private val numObjs      = objectives.size
  private val minObjValues = objectives.map(_.getLB())
  // val dominantObjValues   = objectives.map(_.getUB())
  val paretoFront    = Buffer[Solution]()
  val paretoObjFront = Buffer[Array[Int]]()
  var lastSolutionTime = LocalDateTime.now()

  override def propagate(evtmask: Int): Unit = {
    // println(objectives.map(_.toString()).mkString(", "))
    // iterate though the current pareto front
    wfor(0, _ < paretoFront.size, _ + 1) { soli =>
      var count        = numObjs
      var lastUnderIdx = 0
      var lastUnderVal = objectives(0).getLB()
      wfor(0, _ < numObjs, _ + 1) { j =>
        if (paretoObjFront(soli)(j) <= objectives(j).getLB()) {
          count -= 1
        } else if (objectives(j).getLB() < paretoObjFront(soli)(j)) {
          lastUnderIdx = j
          lastUnderVal = paretoObjFront(soli)(j)
        }
      }
      // println(s"$soli : $count -> $lastUnderIdx, $lastUnderVal")
      if (count <= 1) {
        objectives(lastUnderIdx).updateUpperBound(lastUnderVal - 1, this)
      }
    }
  }

  override def isEntailed(): ESat = {
    // println("check entailment")
    // println(objectives.map(_.getValue()).mkString(", "))
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
    val makedForErasure = Array.fill(paretoFront.size)(false)
    // check if it is a fully dominant solution
    for ((approxParetoSol, i) <- paretoObjFront.zipWithIndex) {
      if (approxParetoSol.zipWithIndex.forall((o, j) => solObjs(j) < o)) {
        makedForErasure(i) = true
      }
    }
    // remove, from last to first so that the indexes of the firsts are not changed
    // during iteration
    for ((erase, i) <- makedForErasure.zipWithIndex.reverse; if erase) {
      paretoFront.remove(i)
      paretoObjFront.remove(i)
    }
    // println("Same frontier")
    paretoFront += model.getSolver().defaultSolution().record()
    paretoObjFront += solObjs
    // println(paretoObjFront.map(_.mkString(", ")).mkString("\n"))
    // println(paretoFront.map(_.mkString(", ")).mkString("\n"))
    lastSolutionTime = LocalDateTime.now()
  }
}

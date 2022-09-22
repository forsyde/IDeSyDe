package idesyde.exploration.choco.explorers

import org.chocosolver.solver.search.loop.monitors.IMonitorSolution
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.Model

class ParetoMinimizationBrancher(val model: Model, val objectives: Array[IntVar]) extends IMonitorSolution {

    def onSolution(): Unit = {
        model.or(objectives.map(o => model.arithm(o, "<", o.getValue())):_*).post()
    }
}

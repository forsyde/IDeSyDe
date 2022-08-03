package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar

trait ManyProcessManyMessageMemoryConstraintsMixin extends ChocoModelMixin {
  
    def processSize: Array[Int]
    def dataSize: Array[Int]

    def processesMemoryMapping: Array[Array[BoolVar]]
    def messagesMemoryMapping: Array[Array[BoolVar]]
    def memoryUsage: Array[IntVar]

    def memories = 0 until memoryUsage.size

    def postManyProcessManyMessageMemoryConstraints(): Unit = {
        memories.foreach(mem => {
            val pMaps = processesMemoryMapping.map(p => p(mem).asIntVar())
            val cMaps = messagesMemoryMapping.map(c => c(mem).asIntVar())
            chocoModel.scalar(pMaps ++ cMaps, processSize ++ dataSize, "<=", memoryUsage(mem)).post()
        })
    }
}

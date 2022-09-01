package idesyde.identification.choco.models

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar

trait SingleProcessSingleMessageMemoryConstraintsMixin extends ChocoModelMixin {

  def processSize: Array[Int]
  def dataSize: Array[Int]

  def processesMemoryMapping: Array[IntVar]
  def messagesMemoryMapping: Array[IntVar]
  def memoryUsage: Array[IntVar]

  def memories = 0 until memoryUsage.size

  def postManyProcessManyMessageMemoryConstraints(): Unit = {
    chocoModel
      .binPacking(
        processesMemoryMapping ++ messagesMemoryMapping,
        processSize ++ dataSize,
        memoryUsage,
        0
      )
      .post()
    // memories.foreach(mem => {
    //   // val pMaps = processesMemoryMapping.map(p => p(mem).asIntVar())
    //   // val cMaps = messagesMemoryMapping.map(c => c(mem).asIntVar())

    // })
  }
}

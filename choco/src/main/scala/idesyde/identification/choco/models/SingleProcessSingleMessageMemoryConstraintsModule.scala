package idesyde.identification.choco.models

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.Model

class SingleProcessSingleMessageMemoryConstraintsModule(
    val chocoModel: Model,
    val processSize: Array[Int],
    val dataSize: Array[Int],
    val memorySizes: Array[Int]
) extends ChocoModelMixin {

  private val processes = (0 until processSize.size).toArray
  private val messages  = (0 until dataSize.size).toArray
  private val memorySet = (0 until memorySizes.size).toArray

  private val messagesMemoryMapping: Array[IntVar] = messages
    .map(c => chocoModel.intVar(s"mapMessage($c)", memorySet))
    .toArray

  val processesMemoryMapping: Array[IntVar] = processes
    .map(a => chocoModel.intVar(s"mapProcess($a)", memorySet))
    .toArray

  val memoryUsage: Array[IntVar] = memorySet.zipWithIndex
    .map((m, s) => chocoModel.intVar(s"memUsage($s)", 0, m, true))

  // def memories = 0 until memoryUsage.size

  def postSingleProcessSingleMessageMemoryConstraints(): Unit = {
    chocoModel
      .binPacking(
        processesMemoryMapping ++ messagesMemoryMapping,
        processSize ++ dataSize,
        memoryUsage,
        memoryUsage.head.getLB()
      )
      .post()
    // memories.foreach(mem => {
    //   // val pMaps = processesMemoryMapping.map(p => p(mem).asIntVar())
    //   // val cMaps = messagesMemoryMapping.map(c => c(mem).asIntVar())

    // })
  }
}

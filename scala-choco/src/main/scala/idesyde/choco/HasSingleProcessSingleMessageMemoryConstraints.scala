package idesyde.choco

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.Model

trait HasSingleProcessSingleMessageMemoryConstraints {

  def postSingleProcessSingleMessageMemoryConstraints(
      chocoModel: Model,
      processSizes: Array[Int],
      dataSizes: Array[Int],
      memorySizes: Array[Int]
  ): (Array[IntVar], Array[IntVar], Array[IntVar]) = {
    val processes = (0 until processSizes.size).toArray
    val messages  = (0 until dataSizes.size).toArray
    val memorySet = (0 until memorySizes.size).toArray

    val vars = chocoModel.getVars()

    val messagesMemoryMapping: Array[IntVar] = messages
      .map(c =>
        vars
          .find(_.getName() == s"mapMessage($c)")
          .getOrElse(chocoModel.intVar(s"mapMessage($c)", memorySet))
          .asIntVar()
      )
      .toArray

    val processesMemoryMapping: Array[IntVar] = processes
      .map(a =>
        vars
          .find(_.getName() == s"mapProcess($a)")
          .getOrElse(chocoModel.intVar(s"mapProcess($a)", memorySet))
          .asIntVar()
      )
      .toArray

    val memoryUsage: Array[IntVar] = memorySizes.zipWithIndex
      .map((m, s) =>
        vars
          .find(_.getName() == s"memUsage($s)")
          .getOrElse(chocoModel.intVar(s"memUsage($s)", 0, m, true))
          .asIntVar()
      )

    chocoModel
      .binPacking(
        processesMemoryMapping ++ messagesMemoryMapping,
        processSizes ++ dataSizes,
        memoryUsage,
        0
      )
      .post()
    (processesMemoryMapping, messagesMemoryMapping, memoryUsage)
  }
}

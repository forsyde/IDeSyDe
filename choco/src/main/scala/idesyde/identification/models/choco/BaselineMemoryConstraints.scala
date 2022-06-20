package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar

trait BaselineMemoryConstraints extends ChocoModelMixin {

  def taskSize: Array[Int]
  def dataSize: Array[Int]

  def taskMapping: Array[IntVar]
  def dataMapping: Array[IntVar]
  def memoryUsage: Array[IntVar]

  def postTaskAndDataMemoryConstraints(): Unit =
    chocoModel
      .binPacking(
        taskMapping ++ dataMapping,
        taskSize ++ dataSize,
        memoryUsage,
        0 // 0 offset for no minizinc
      )
      .post

}

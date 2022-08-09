package idesyde.identification.models.choco

import org.chocosolver.solver.Model
import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.apache.commons.math3.fraction.Rational

trait WorkloadConstraintsCommsMixin extends ChocoModelMixin {

  val channelSizes: Array[Long]
  val bandwidths: Array[Rational]
  val hardwarePaths: Array[Array[Array[Array[Int]]]]

  val transmissionStart: Array[IntVar]
  val transmissionSend: Array[IntVar]

  val wctts: Array[Array[IntVar]]
  val bandwidthLoad: Array[IntVar]
  val wccts: Array[Array[Array[IntVar]]]
  val allocations: Array[Array[BoolVar]]

  def postWCTTCalculation(): Unit = {
    bandwidths.zipWithIndex.foreach((bw, j) => {
      val atj = wccts.zipWithIndex.map((channelWcct, i) => {})
    })
  }
}

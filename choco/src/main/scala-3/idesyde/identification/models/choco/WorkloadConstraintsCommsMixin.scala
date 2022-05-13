package idesyde.identification.models.choco

import org.chocosolver.solver.Model
import idesyde.identification.interfaces.ChocoCPForSyDeDecisionModel
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.apache.commons.math3.fraction.BigFraction

trait WorkloadConstraintsCommsMixin extends ChocoCPForSyDeDecisionModel {

  val channelSizes: Array[Long]
  val bandwidths: Array[BigFraction]
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

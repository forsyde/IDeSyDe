package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.apache.commons.math3.fraction.Rational
import org.chocosolver.solver.variables.BoolVar

trait BaselineTimingConstraintsMixin extends ChocoModelMixin {

  def priorities: Array[Int]
  def periods: Array[Rational]
  def maxUtilizations: Array[Rational]
  def durations: Array[Array[IntVar]]
  def taskExecution: Array[Array[BoolVar]]
  def blockingTimes: Array[IntVar]
  def responseTimes: Array[IntVar]
  
  val processors = 0 until maxUtilizations.size

  lazy val utilizations = maxUtilizations
      .map(_.multiply(100).doubleValue.ceil.toInt)
      .zipWithIndex
      .map((maxU, j) => {
          chocoModel.intVar(s"pe_${j}_utilization", 0, maxU)
      })

//   lazy val peUtilizations = maxUtilizations
//       .map(_.multiply(100).doubleValue.ceil.toInt)
//       .zipWithIndex
//       .map((maxU, j) => {
//           chocoModel.intVar(s"pe_${j}_utilization", 0, maxU)
//       })

  def postMinimalResponseTimesByBlocking(): Unit = {
    responseTimes.zipWithIndex.foreach((r, i) => {
      r.ge(blockingTimes(i)).post
    })
    durations.zipWithIndex.foreach((w, i) => {
      (0 until maxUtilizations.length).map(j => {
        chocoModel.ifThen(
          taskExecution(i)(j),
          responseTimes(i).ge(blockingTimes(i).add(w(j))).decompose
        )
      })
    })
  }

  def postMaximumUtilizations(): Unit = {
    maxUtilizations
      .map(_.multiply(100).doubleValue.ceil.toInt)
      .zipWithIndex
      .foreach((maxU, j) => {
        chocoModel.scalar(
          durations.map(d => d(j)),
          durations.zipWithIndex.map((_, i) => Rational(100).divide(periods(i)).doubleValue.toInt),
          "<=",
          utilizations(j)
        ).post
        //chocoModel.arithm(utilizationSum, "<=", maxU).post
      })
  }
  
  def postTaskMapToAtLeastOne(): Unit = {
    taskExecution.zipWithIndex.foreach((ts, i) => chocoModel.or(ts:_*).post)
  }

//   class UtilizationPropagator() extends Propagator[IntVar](
//         taskExecution ++  ++ durations.flatten,
//         PropagatorPriority.BINARY,
//         false
//       )

}

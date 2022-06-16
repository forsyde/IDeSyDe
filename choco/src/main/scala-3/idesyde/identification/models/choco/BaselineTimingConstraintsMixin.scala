package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.apache.commons.math3.fraction.BigFraction

trait BaselineTimingConstraintsMixin extends ChocoModelMixin {

  val priorities: Array[Int]
  val periods: Array[BigFraction]
  val maxUtilizations: Array[BigFraction]
  val durations: Array[Array[IntVar]]
  val taskExecution: Array[IntVar]
  val blockingTimes: Array[IntVar]
  val responseTimes: Array[IntVar]

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
    durations.zipWithIndex.foreach((ws, i) => {
      ws.zipWithIndex.foreach((w, j) => {
        chocoModel.ifThen(
          taskExecution(i).eq(j).decompose,
          responseTimes(i).ge(blockingTimes(i).add(w)).decompose
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
          durations.zipWithIndex.map((ws, i) => ws(j)),
          durations.zipWithIndex.map((ws, i) => BigFraction(100).divide(periods(i)).doubleValue.toInt),
          "<=",
          utilizations(j)
        ).post
        //chocoModel.arithm(utilizationSum, "<=", maxU).post
      })

  }

//   class UtilizationPropagator() extends Propagator[IntVar](
//         taskExecution ++  ++ durations.flatten,
//         PropagatorPriority.BINARY,
//         false
//       )

}

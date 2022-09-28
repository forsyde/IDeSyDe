package idesyde.identification.choco.models

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import spire.math.*
import org.chocosolver.solver.Model

class BaselineTimingConstraintsModule(
  val chocoModel: Model,
  val priorities: Array[Int],
  val periods: Array[Rational],
  val maxUtilizations: Array[Rational],
  val durations: Array[Array[IntVar]],
  val taskExecution: Array[IntVar],
  val blockingTimes: Array[IntVar],
  val responseTimes: Array[IntVar]
) extends ChocoModelMixin {

  private val processors = 0 until maxUtilizations.size

  val utilizations = maxUtilizations
    .map(_ * (100))
    .map(_.ceil.toInt)
    .zipWithIndex
    .map((maxU, j) => {
      chocoModel.intVar(s"pe_${j}_utilization", 0, maxU, true)
    })

  def postMinimalResponseTimesByBlocking(): Unit = {
    responseTimes.zipWithIndex.foreach((r, i) => {
      r.ge(blockingTimes(i)).post
    })
    durations.zipWithIndex.foreach((w, i) => {
      (0 until maxUtilizations.length).map(j => {
        chocoModel.ifThen(
          taskExecution(i).eq(j).decompose(),
          responseTimes(i).ge(blockingTimes(i).add(w(j))).decompose
        )
      })
    })
  }

  def postMaximumUtilizations(): Unit = {
    maxUtilizations
      .map(u => (u * (100)).ceil.toInt)
      .zipWithIndex
      .foreach((maxU, j) => {
        chocoModel
          .scalar(
            durations.map(d => d(j)),
            durations.zipWithIndex
              .map((_, i) => (100 / (periods(i))).toInt),
            "<=",
            utilizations(j)
          )
          .post
        //chocoModel.arithm(utilizationSum, "<=", maxU).post
      })
  }

  // def postTaskMapToAtLeastOne(): Unit = {
  //   taskExecution.zipWithIndex.foreach((ts, i) => chocoModel.sum(ts, ">", 0).post)
  // }

//   class UtilizationPropagator() extends Propagator[IntVar](
//         taskExecution ++  ++ durations.flatten,
//         PropagatorPriority.BINARY,
//         false
//       )

}

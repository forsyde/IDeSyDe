package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.apache.commons.math3.fraction.BigFraction
import org.chocosolver.solver.constraints.Propagator
import scala.annotation.targetName
import org.chocosolver.solver.variables.Variable
import org.chocosolver.util.ESat
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.variables.BoolVar

trait FixedPriorityConstraintsMixin extends ChocoModelMixin {

  def priorities: Array[Int]
  def periods: Array[BigFraction]
  def deadlines: Array[BigFraction]
  def wcets: Array[Array[BigFraction]]
  def taskExecution: Array[Array[BoolVar]]
  def responseTimes: Array[IntVar]
  def blockingTimes: Array[IntVar]
  def durations: Array[Array[IntVar]]

  def sufficientRMSchedulingPoints(taskIdx: Int): Array[BigFraction] = Array.empty

  def postFixedPrioriPreemtpiveConstraint(schedulerIdx: Int): Unit =
    // taskExecution.zipWithIndex.foreach((taskVar, i) => {
      //postFixedPrioriPreemtpiveConstraintByTask(i, schedulerIdx)
    // })
    chocoModel.post(
      new Constraint(
        s"scheduler_${schedulerIdx}_iter_prop",
        FPSpecificPropagator(
          schedulerIdx,
          priorities,
          periods,
          deadlines,
          wcets,
          taskExecution,
          responseTimes,
          blockingTimes,
          durations
        )
      )
    )

  // TODO: check if this is still correct after durations become vector instead of matrix
  private def postFixedPrioriPreemtpiveConstraintByTask(taskIdx: Int, schedulerIdx: Int): Unit =
    if (sufficientRMSchedulingPoints(taskIdx).isEmpty) then
      chocoModel.ifThen(
        taskExecution(taskIdx)(schedulerIdx),
        responseTimes(taskIdx)
          .ge(
            durations(taskIdx)(schedulerIdx)
              .add(blockingTimes(taskIdx))
          )
          .decompose
      )
    else {
      chocoModel.ifThen(
        taskExecution(taskIdx)(schedulerIdx),
        chocoModel.or(
          sufficientRMSchedulingPoints(taskIdx).map(t => {
            responseTimes(taskIdx)
              .ge(
                durations(taskIdx)(schedulerIdx)
                  .add(blockingTimes(taskIdx))
                  .add(
                    chocoModel
                      .sum(
                        s"fp_interference${taskIdx}_${schedulerIdx}",
                        durations.zipWithIndex
                          .filter((ws, k) => k != taskIdx)
                          .filter((ws, k) =>
                            // leave tasks k which i occasionally block
                            priorities(k) >= priorities(taskIdx)
                          )
                          .map((w, k) => {
                            w(schedulerIdx).mul({
                                // we use floor + 1 intead of ceil due to the fact
                                // that ceil(s) is not a strict GT but a GE implementation
                                t.divide(
                                  periods(k)
                                ).doubleValue
                                  .floor
                                  .toInt + 1
                              })
                              .intVar
                          })
                          .toArray: _*
                      )
                  )
              )
              .decompose
          }): _*
        )
      )
    }

  class FPSpecificPropagator(
      val schedulerIdx: Int,
      val priorities: Array[Int],
      val periods: Array[BigFraction],
      val deadlines: Array[BigFraction],
      val wcets: Array[Array[BigFraction]],
      val taskExecution: Array[Array[BoolVar]],
      val responseTimes: Array[IntVar],
      val blockingTimes: Array[IntVar],
      val durations: Array[Array[IntVar]]
  ) extends Propagator[IntVar](
        taskExecution.flatten ++ responseTimes ++ blockingTimes ++ durations.flatten,
        PropagatorPriority.BINARY,
        false
      ) {

    def isEntailed(): org.chocosolver.util.ESat = {
      if (
        responseTimes.zipWithIndex
          .forall((r, i) => r.isInstantiated && r.getUB <= deadlines(i).doubleValue.floor.toInt + 1)
      ) return ESat.TRUE;
      else if (
        responseTimes.zipWithIndex
          .exists((r, i) => r.getLB > deadlines(i).doubleValue.floor.toInt + 1)
      )
        return ESat.FALSE
      else return ESat.UNDEFINED;
    }

    def propagate(x$0: Int): Unit =
      taskExecution.zipWithIndex
        .filter((pi, i) => pi(schedulerIdx).contains(1))
        .foreach((pi, i) => {
          val dur = Math.max(
            durations(i)(schedulerIdx).getLB(),
            wcets(i)(schedulerIdx).doubleValue.floor.toInt + 1
          )
          var rtL     = -1
          var rtLNext = blockingTimes(i).getLB() + dur
          while (rtL != rtLNext && rtL < responseTimes(i).getUB()) {
            rtL = rtLNext
            rtLNext = blockingTimes(i).getLB() + dur +
              taskExecution.zipWithIndex
                .filter((_, j) => i != j && priorities(j) >= priorities(i))
                .filter((pj, j) => pj(schedulerIdx).isInstantiatedTo(1))
                .map((_, j) =>
                  Math.max(
                    durations(j)(schedulerIdx).getLB(),
                    wcets(j)(schedulerIdx).doubleValue.floor.toInt + 1
                  )
                    * (periods(j).reciprocal.multiply(rtL).doubleValue.floor.toInt + 1)
                )
                .sum
          }
          //scribe.debug(s"RT of ${i} in ${schedulerIdx} prop: ${rtL}")
          if (responseTimes(i).getLB() < rtL) responseTimes(i).updateLowerBound(rtL, this)
          //if (rtU < responseTimes(i).getUB()) responseTimes(i).updateUpperBound(rtU, this)
        })

      
  }
}

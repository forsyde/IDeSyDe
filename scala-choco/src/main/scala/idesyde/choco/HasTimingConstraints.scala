package idesyde.choco

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.Model
import org.chocosolver.solver.constraints.Constraint

trait HasTimingConstraints {

  def postMinimalResponseTimesByBlocking(
      chocoModel: Model,
      priorities: Array[Int],
      periods: Array[Int],
      maxUtilizations: Array[Double],
      durations: Array[IntVar],
      taskExecution: Array[IntVar],
      blockingTimes: Array[IntVar],
      responseTimes: Array[IntVar]
  ): Unit = {
    responseTimes.zipWithIndex.foreach((r, i) => {
      r.ge(blockingTimes(i)).post
    })
    durations.zipWithIndex.foreach((w, i) => {
      (0 until maxUtilizations.length).map(j => {
        chocoModel.ifThen(
          taskExecution(i).eq(j).decompose(),
          responseTimes(i).ge(blockingTimes(i).add(w)).decompose
        )
      })
    })
  }

  def postMaximumUtilizations(
      chocoModel: Model,
      priorities: Array[Int],
      periods: Array[Int],
      maxUtilizations: Array[Double],
      durations: Array[IntVar],
      taskExecution: Array[IntVar],
      blockingTimes: Array[IntVar],
      responseTimes: Array[IntVar]
  ): Array[IntVar] = {
    val utilizations = maxUtilizations
      .map(_ * (100))
      .map(_.floor.toInt)
      .zipWithIndex
      .map((maxU, j) => {
        chocoModel.intVar(s"pe_${j}_utilization", 0, Math.min(maxU, 100), true)
      })
    // TODO: find a way to reduce the pessimism here
    chocoModel
      .binPacking(
        taskExecution,
        durations.zipWithIndex
          .map((d, i) => (d.getUB(), i))
          .map((d, i) => d / periods(i)),
        utilizations,
        0
      )
      .post()
    //   }
    //   maxUtilizations
    //     .map(u => (u * (100)).ceil.toInt)
    //     .zipWithIndex
    //     .foreach((maxU, j) => {
    //       chocoModel
    //         .scalar(
    //           durations.map(d => d.),
    //           durations.zipWithIndex
    //             .map((_, i) => (100 / (periods(i))).toInt),
    //           "<=",
    //           utilizations(j)
    //         )
    //         .post
    //       //chocoModel.arithm(utilizationSum, "<=", maxU).post
    //     })
    utilizations
  }

  def postFixedPrioriPreemtpiveConstraint(
      schedulerIdx: Int,
      chocoModel: Model,
      priorities: Array[Int],
      periods: Array[Int],
      deadlines: Array[Int],
      wcets: Array[Array[Int]],
      maxUtilizations: Array[Double],
      durations: Array[IntVar],
      taskExecution: Array[IntVar],
      blockingTimes: Array[IntVar],
      responseTimes: Array[IntVar]
  ): Constraint = {
    val const = new Constraint(
      s"scheduler_${schedulerIdx}_iter_prop",
      FixedPriorityPreemptivePropagator(
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
    chocoModel.post(const)
    const
  }

  /** This method sets up the Worst case schedulability test for a task.
    *
    * The mathetical 'representation' is responseTime(i) >= blockingTime(i) + durations(i) +
    * sum(durations of all higher prios in same scheduler)
    *
    * @param taskIdx
    *   the task to be posted (takes into account all others)
    * @param schedulerIdx
    *   the scheduler to be posted
    */
  def postStaticCyclicExecutiveConstraint(
      chocoModel: Model,
      interTaskAlwaysBlocks: (Int) => (Int) => Boolean,
      durations: Array[IntVar],
      taskExecution: Array[IntVar],
      responseTimes: Array[IntVar],
      blockingTimes: Array[IntVar]
  )(taskIdx: Int, schedulerIdx: Int): Unit =
    chocoModel.ifThen(
      taskExecution(taskIdx).eq(schedulerIdx).decompose(),
      responseTimes(taskIdx)
        .ge(
          durations(taskIdx)
            .add(blockingTimes(taskIdx))
            .add(
              chocoModel
                .sum(
                  s"sc_interference${taskIdx}_${schedulerIdx}",
                  durations.zipWithIndex
                    .filter((ws, k) => k != taskIdx)
                    .filterNot((ws, k) =>
                      // leave tasks k which i occasionally block
                      interTaskAlwaysBlocks(taskIdx)(k)
                    )
                    .map((w, k) => w)
                    .toArray: _*
                )
            )
        )
        .decompose
    )

}

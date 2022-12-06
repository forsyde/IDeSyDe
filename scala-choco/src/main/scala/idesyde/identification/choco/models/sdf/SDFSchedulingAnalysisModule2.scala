package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import scala.collection.mutable.HashMap
import breeze.linalg._
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.solver.constraints.`extension`.Tuples
import idesyde.utils.CoreUtils.wfor
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW
import org.chocosolver.solver.Model
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsModule
import idesyde.identification.choco.models.TileAsyncInterconnectCommsModule
import scala.collection.mutable.Buffer

class SDFSchedulingAnalysisModule2(
    val chocoModel: Model,
    val sdfAndSchedulers: SDFToSchedTiledHW,
    val memoryMappingModule: SingleProcessSingleMessageMemoryConstraintsModule,
    val tileAsyncModule: TileAsyncInterconnectCommsModule,
    val timeFactor: Long = 1L
) extends ChocoModelMixin() {

  private val actors: Array[Int] = sdfAndSchedulers.sdfApplications.actorsSet
  val jobsAndActors: Array[(Int, Int)] =
    sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.nodes
      .map(v => v.value)
      .toArray
  private val messages: Array[Int] =
    sdfAndSchedulers.sdfApplications.sdfMessages.zipWithIndex.map((_, i) => i)
  private val schedulers: Array[Int] = sdfAndSchedulers.platform.schedulerSet
  private val actorDuration: Array[Array[Int]] =
    sdfAndSchedulers.wcets.map(ws => ws.map(w => w * timeFactor).map(_.ceil.intValue))

  private val maxRepetitionsPerActors     = sdfAndSchedulers.sdfApplications.sdfRepetitionVectors
  private def isSelfConcurrent(aIdx: Int) = sdfAndSchedulers.sdfApplications.isSelfConcurrent(aIdx)

  val slotRange                       = (0 until maxRepetitionsPerActors.sum).toArray
  private val maximumTokensPerChannel = sdfAndSchedulers.sdfApplications.pessimisticTokensPerChannel

  private val maxThroughput = schedulers.zipWithIndex
    .map((_, p) => {
      actors.zipWithIndex
        .map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai))
        .sum
    })
    .max + tileAsyncModule.messageTravelDuration.flatten.flatten.map(_.getUB()).sum

  val invThroughputs: Array[IntVar] = actors.zipWithIndex
    .map((a, i) =>
      chocoModel.intVar(
        s"invTh($a)",
        actorDuration(i).filter(_ >= 0).minOption.getOrElse(0) * maxRepetitionsPerActors(i),
        maxThroughput,
        true
      )
    )

  // val procLenthgs: Array[IntVar] = schedulers.zipWithIndex
  //   .map((p, i) =>
  //     chocoModel.intVar(
  //       s"procLenthgs($p)",
  //       0,
  //       maxSingleCoreInterval,
  //       true
  //     )
  //   )

  val jobOrderingInP = schedulers.zipWithIndex.map((p, j) =>
    jobsAndActors
      .map((a, q) =>
        chocoModel.intVar(
          s"jobOrder($p, $a, $q)",
          (0 to jobsAndActors.size).toArray
        )
      )
  )

  val jobPrev =
    jobsAndActors
      .map((a, q) =>
        chocoModel.intVar(
          s"jobPrev($a, $q)",
          (0 to jobsAndActors.size).toArray
        )
      )

  // val jobReaches = jobsAndActors
  //   .map((a, q) =>
  //     jobsAndActors
  //       .map((aa, qq) =>
  //         chocoModel.boolVar(
  //           s"jobReaches($a, $q, $aa, $qq)"
  //         )
  //       )
  //   )
  val jobOrdering = jobsAndActors
    .map((a, q) =>
      chocoModel.intVar(
        s"jobOrder($a, $q)",
        (0 to jobsAndActors.size).toArray
      )
    )

  val jobStartTime: Array[IntVar] =
    jobsAndActors
      .map((a, q) =>
        chocoModel.intVar(
          s"jobStartTime($a, $q)",
          0,
          maxThroughput,
          true
        )
      )

  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      invThroughputs.map(_.getLB()).max,
      maxThroughput,
      true
    )

  val duration = actors.zipWithIndex.map((a, i) =>
    chocoModel.intVar(
      s"dur($a)",
      actorDuration(i).filter(_ >= 0).minOption.getOrElse(0),
      actorDuration(i).max,
      true
    )
  )

  val mappedPerProcessingElement = schedulers.zipWithIndex.map((p, i) =>
    chocoModel.count(
      s"mappedPerProcessingElement($p)",
      p,
      jobsAndActors.map((a, _) => memoryMappingModule.processesMemoryMapping(actors.indexOf(a))): _*
    )
  )

  def postSDFTimingAnalysis(): Unit = {
    // --- general duration constraints
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", p),
        chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
      )
      for ((job, k) <- jobsAndActors.zipWithIndex; if job._1 == a) {
        chocoModel.ifThenElse(
          chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", p),
          chocoModel.arithm(jobOrderingInP(j)(k), ">", 0),
          chocoModel.arithm(jobOrderingInP(j)(k), "=", 0)
        )
        chocoModel.ifThen(
          chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", p),
          chocoModel.arithm(jobOrderingInP(j)(k), "=", jobOrdering(k))
        )
      }
    }
    // -------------- next and ordering parts
    for ((p, j) <- schedulers.zipWithIndex) {
      chocoModel.max(mappedPerProcessingElement(j), jobOrderingInP(j)).post()
      chocoModel.allDifferentExcept0(jobOrderingInP(j)).post()
      // chocoModel.inverseChanneling(jobNextInP(j), jobPrevInP(j)).post()
    }
    // chocoModel.allDifferent(jobNext, "DEFAULT").post()
    chocoModel.allDifferentExcept0(jobPrev).post()
    // chocoModel.inverseChanneling(jobNext, jobPrev).post()
    // -- nexts are only valid when they are mapped in the same PE
    // -- must make a path
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      src = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v);
      (vv, j) <- jobsAndActors.zipWithIndex;
      dst = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(vv);
      if i != j
    ) {
      // if j should succeed i eventually, and if they are mapped in the same core, force the reachability.
      if (src.pathTo(dst).isDefined) {
        chocoModel.ifThen(
          chocoModel.arithm(
            memoryMappingModule
              .processesMemoryMapping(actors.indexOf(vv._1)),
            "=",
            memoryMappingModule
              .processesMemoryMapping(actors.indexOf(v._1))
          ),
          chocoModel.arithm(jobOrdering(i), "<", jobOrdering(j))
        )
      }
      chocoModel.ifThenElse(
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule
              .processesMemoryMapping(actors.indexOf(vv._1)),
            "=",
            memoryMappingModule
              .processesMemoryMapping(actors.indexOf(v._1))
          ),
          chocoModel.arithm(jobOrdering(i), "=", jobOrdering(j), "-", 1)
        ),
        chocoModel.arithm(jobPrev(j), "=", i + 1),
        chocoModel.arithm(jobPrev(j), "!=", i + 1)
      )
    }
    // -----/
    // -------------- timings based on next
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      (a, q) = v
    ) {
      chocoModel.arithm(jobPrev(i), "!=", i + 1).post()
      val prevTime =
        chocoModel.element(s"prevTime($a, $q)", chocoModel.intVar(0) +: jobStartTime, jobPrev(i), 0)
      val prevDuration = chocoModel.element(
        s"prevDuration($a, $q)",
        chocoModel.intVar(0) +: jobsAndActors.map((a, _) => duration(actors.indexOf(a))),
        jobPrev(i),
        0
      )
      val fromPrevToI =
        chocoModel.intVar(s"fromPrevToI($a, $q)", 0, prevTime.getUB() + prevDuration.getUB(), true)
      // chocoModel.ifThenElse(
      //   chocoModel.arithm(jobOrdering(i), "=", 1),
      //   chocoModel.arithm(fromPrevToI, "=", 0),
      //   )
      chocoModel.arithm(fromPrevToI, "=", prevTime, "+", prevDuration).post()
      val predecessorsTimes = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
        .get(v)
        .diPredecessors
        .map(pred => {
          val (aa, _) = pred.value
          val j       = jobsAndActors.indexOf(pred.value)
          val messageTimesIdx =
            sdfAndSchedulers.sdfApplications.sdfMessages.indexWhere((src, dst, _, _, _, _, _) =>
              src == aa && dst == a
            )
          val maxMessageTime =
            if (messageTimesIdx > -1) then
              chocoModel.max(
                s"maxMessageTime($j, $i)",
                tileAsyncModule.messageTravelDuration(messageTimesIdx).flatten
              )
            else chocoModel.intVar(0)
          val fromJToI = chocoModel.intVar(
            s"fromJToI($j, $i)",
            0,
            jobStartTime(j).getUB() + maxMessageTime.getUB() + duration(actors.indexOf(aa)).getUB(),
            true
          )
          if (a != aa) {
            chocoModel.ifThenElse(
              chocoModel.arithm(
                memoryMappingModule
                  .processesMemoryMapping(actors.indexOf(a)),
                "!=",
                memoryMappingModule
                  .processesMemoryMapping(actors.indexOf(aa))
              ),
              chocoModel
                .scalar(
                  Array(
                    maxMessageTime,
                    jobStartTime(j),
                    duration(actors.indexOf(aa))
                  ),
                  Array(1, 1, 1),
                  "=",
                  fromJToI
                ),
              chocoModel
                .scalar(
                  Array(
                    jobStartTime(j),
                    duration(actors.indexOf(aa))
                  ),
                  Array(1, 1, 1),
                  "=",
                  fromJToI
                )
            )
          } else if (a == aa && !isSelfConcurrent(a)) {
            chocoModel
              .arithm(fromJToI, "=", duration(actors.indexOf(aa)), "+", jobStartTime(j))
              .post()
          } else if (a == aa && isSelfConcurrent(a)) {
            chocoModel.arithm(fromJToI, "=", jobStartTime(j)).post()
          }
          fromJToI
        })
        .toArray
      chocoModel.max(jobStartTime(i), predecessorsTimes :+ fromPrevToI).post()
    }
    // -----/
    // throughput
    for ((a, i) <- actors.zipWithIndex) {
      val firstJob = jobsAndActors.indexWhere((aa, q) => a == aa && q == 1)
      val nextCycleJob =
        jobsAndActors.indexWhere((aa, q) => a == aa && q == maxRepetitionsPerActors(i))
      chocoModel
        .scalar(
          Array(jobStartTime(nextCycleJob), duration(i), jobStartTime(firstJob)),
          Array(1, 1, -1),
          "<=",
          invThroughputs(i)
        )
        .post()
      for (
        predV <- sdfAndSchedulers.sdfApplications.firingsPrecedenceWithExtraStepGraph
          .get((a, maxRepetitionsPerActors(i) + 1))
          .diPredecessors;
        (aa, qq) = predV.value;
        if a != aa && qq <= maxRepetitionsPerActors(actors.indexOf(aa))
      ) {
        val lastJob =
          jobsAndActors.indexWhere((aaa, q) => aa == aaa && q == qq)
        chocoModel
          .scalar(
            Array(jobStartTime(lastJob), duration(actors.indexOf(aa)), jobStartTime(firstJob)),
            Array(1, 1, -1),
            "<=",
            invThroughputs(i)
          )
          .post()
      }
      for ((aa, j) <- actors.zipWithIndex; if a != aa) {
        if (
          sdfAndSchedulers.sdfApplications.sdfGraph
            .get(a)
            .pathTo(sdfAndSchedulers.sdfApplications.sdfGraph.get(aa))
            .isDefined
        ) {
          chocoModel
            .arithm(
              invThroughputs(i),
              "=",
              invThroughputs(j)
            )
            .post()
        }
        chocoModel.ifThen(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(i),
            "=",
            memoryMappingModule.processesMemoryMapping(j)
          ),
          chocoModel.arithm(
            invThroughputs(i),
            "=",
            invThroughputs(j)
          )
        )
        val lastJob =
          jobsAndActors.indexWhere((aaa, q) => aa == aaa && q == maxRepetitionsPerActors(j))
        chocoModel.ifThen(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(i),
            "=",
            memoryMappingModule.processesMemoryMapping(j)
          ),
          chocoModel
            .scalar(
              Array(jobStartTime(lastJob), duration(j), jobStartTime(firstJob)),
              Array(1, 1, -1),
              "<=",
              invThroughputs(i)
            )
        )
      }
    }
    // chocoModel.post(
    //   new Constraint(
    //     "OrderedJobUpperBoundPropagator",
    //     OrderedJobUpperBoundPropagator(
    //       jobsAndActors.map((a, _) =>
    //         memoryMappingModule.processesMemoryMapping(actors.indexOf(a))
    //       ),
    //       jobOrdering,
    //       jobStartTime,
    //       jobsAndActors.map((a, _) => duration(actors.indexOf(a)))
    //     )
    //   )
    // )
    // throughput
    for ((a, i) <- actors.zipWithIndex) {
      val firstJob = jobsAndActors.indexWhere((aa, q) => a == aa && q == 1)
      val nextCycleJob =
        jobsAndActors.indexWhere((aa, q) => a == aa && q == maxRepetitionsPerActors(a))
      chocoModel
        .arithm(jobStartTime(nextCycleJob), "-", jobStartTime(firstJob), "<=", invThroughputs(i))
        .post()

    }
    chocoModel.max(globalInvThroughput, invThroughputs).post()
    // chocoModel.post(
    //   new Constraint(
    //     "MultiCoreJobThroughputPropagator",
    //     MultiCoreJobThroughputPropagator(
    //       jobsAndActors.map((a, _) =>
    //         memoryMappingModule.processesMemoryMapping(actors.indexOf(a))
    //       ),<
    //       jobStartTime,
    //       jobsAndActors.map((a, _) => duration(actors.indexOf(a))),
    //       procLenthgs
    //     )
    //   )
    // )

  }

  def makeCanonicalOrderingAtScheduleConstraint(scheduler: Int): Constraint = {
    // println(sdfAndSchedulers.sdfApplications.topologicalAndHeavyJobOrdering.mkString(", "))
    val constraints = for (
      (v, i)  <- jobsAndActors.zipWithIndex;
      (vv, j) <- jobsAndActors.zipWithIndex;
      if i != j;
      vIdx  = sdfAndSchedulers.sdfApplications.topologicalAndHeavyJobOrdering.indexOf(v);
      vvIdx = sdfAndSchedulers.sdfApplications.topologicalAndHeavyJobOrdering.indexOf(vv);
      if vIdx < vvIdx;
      (a, q)   = v;
      (aa, qq) = vv;
      aIdx     = sdfAndSchedulers.sdfApplications.actorsSet.indexOf(a);
      aaIdx    = sdfAndSchedulers.sdfApplications.actorsSet.indexOf(aa)
    )
      yield chocoModel.or(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(aIdx), "!=", scheduler),
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(aaIdx), "!=", scheduler),
        chocoModel.arithm(jobOrdering(i), "<", jobOrdering(j))
      )
    chocoModel.and(constraints: _*)
  }

}

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
    val timeFactor: Long = 1L,
    val memoryDivider: Long = 1L
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

  val transmissionDelay = actors.zipWithIndex.map((a, i) =>
    actors.zipWithIndex
      .map((aa, j) => {
        val messageTimesIdx =
          sdfAndSchedulers.sdfApplications.sdfMessages
            .indexWhere((src, dst, _, _, _, _, _) => src == a && dst == aa)
        val maxMessageTime =
          if (messageTimesIdx > -1) then
            chocoModel.max(
              s"maxMessageTime($i, $j)",
              tileAsyncModule.messageTravelDuration(messageTimesIdx).flatten
            )
          else chocoModel.intVar(0)
        maxMessageTime
      })
  )

  val jobTasks =
    jobsAndActors.zipWithIndex
      .map((job, i) =>
        chocoModel.taskVar(
          jobStartTime(i),
          duration(actors.indexOf(job._1))
        )
      )

  // val jobTasksHeights = schedulers.map(p =>
  //   jobsAndActors.zipWithIndex
  //     .map((job, i) => chocoModel.boolVar(s"jobHeight($i, $p)"))
  // )

  // val maxBufferTokens = sdfAndSchedulers.sdfApplications.sdfMessages.zipWithIndex.map((tuple, i) =>
  //   val (src, dst, _, size, p, c, tok) = tuple
  //   chocoModel.intVar(
  //     s"maxBufferTokens(${tuple._1}, ${tuple._2})",
  //     0,
  //     tok + sdfAndSchedulers.sdfApplications.sdfRepetitionVectors(actors.indexOf(src)) * p,
  //     true
  //   )
  // )

  val numMappedElements = chocoModel.intVar("numMappedElements", 1, schedulers.size, true)

  def postSDFTimingAnalysis(): Unit = {
    chocoModel.nValues(memoryMappingModule.processesMemoryMapping, numMappedElements).post()
    chocoModel.count("zeroes", 0, jobStartTime: _*).gt(0).post()
    // --- general duration constraints
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", p),
        chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
      )
    }
    // -------------- next and ordering parts
    chocoModel
      .cumulative(
        jobTasks,
        Array.fill(jobsAndActors.size)(chocoModel.intVar(1)),
        numMappedElements
      )
      .post()
    for ((p, j) <- schedulers.zipWithIndex) {
      chocoModel
        .cumulative(
          jobTasks,
          jobsAndActors.map((a, _) =>
            chocoModel.intEqView(memoryMappingModule.processesMemoryMapping(actors.indexOf(a)), p)
          ),
          chocoModel.intVar(1)
        )
        .post()
      // chocoModel
      //   .cumulative(jobTasksPerP(j), jobTasksHeights(j).map(_.asIntVar()), chocoModel.intVar(1))
      //   .post()
      // chocoModel.max(mappedPerProcessingElement(j), jobOrderingInP(j)).post()
      // chocoModel.allDifferentExcept0(jobOrderingInP(j)).post()
      // chocoModel.inverseChanneling(jobNextInP(j), jobPrevInP(j)).post()
    }
    // chocoModel.allDifferent(jobNext, "DEFAULT").post()
    // chocoModel.allDifferentExcept0(jobPrev).post()
    // chocoModel.inverseChanneling(jobNext, jobPrev).post()
    // -- nexts are only valid when they are mapped in the same PE
    // -- must make a path
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      src    = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v);
      (a, q) = src.value
    ) {
      val minPrev = chocoModel.min(
        s"minPrev($i)",
        jobsAndActors.zipWithIndex
          .filter((_, j) => i != j)
          .map((other, j) => jobTasks(i).getStart().sub(jobTasks(j).getEnd()).abs().intVar()): _*
      )
      val maxIncomingComm =
        if (
          sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
            .get(v)
            .diPredecessors
            .size > 0
        ) then
          chocoModel.max(
            s"maxIncomingComm($i)",
            sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
              .get(v)
              .diPredecessors
              .map(_.value)
              .map((aa, qq) => transmissionDelay(actors.indexOf(aa))(actors.indexOf(a)))
              .toArray
          )
        else chocoModel.intVar(0)
      chocoModel.ifThen(
        jobTasks(i).getStart().gt(0).decompose(),
        chocoModel.arithm(minPrev, "<=", maxIncomingComm)
      )
      for (
        (vv, j) <- jobsAndActors.zipWithIndex;
        dst      = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(vv);
        (aa, qq) = dst.value;
        if i != j
      ) {
        // if j should succeed i eventually, and if they are mapped in the same core, force the reachability.
        if (src.pathTo(dst).isDefined) {
          if (a != aa) {
            val messageTimesIdx =
              sdfAndSchedulers.sdfApplications.sdfMessages
                .indexWhere((src, dst, _, _, _, _, _) => src == a && dst == aa)
            if (messageTimesIdx > -1) {
              for (
                (p, k) <- schedulers.zipWithIndex; (pp, l) <- schedulers.zipWithIndex; if k != l
              ) {
                chocoModel.ifThen(
                  chocoModel.and(
                    chocoModel.arithm(
                      memoryMappingModule
                        .processesMemoryMapping(actors.indexOf(a)),
                      "=",
                      p
                    ),
                    chocoModel.arithm(
                      memoryMappingModule
                        .processesMemoryMapping(actors.indexOf(aa)),
                      "=",
                      pp
                    )
                  ),
                  chocoModel
                    .arithm(
                      jobTasks(i).getEnd(),
                      "+",
                      tileAsyncModule.messageTravelDuration(messageTimesIdx)(k)(l),
                      "<=",
                      jobTasks(j).getStart()
                    )
                )
              }
            }
            chocoModel.arithm(jobTasks(i).getEnd(), "<=", jobTasks(j).getStart()).post()
            // disjunctions +=  chocoModel.arithm(jobTasks(i).getEnd(), "=", jobTasks(j).getStart())
          } else if (a == aa && !isSelfConcurrent(a)) {
            chocoModel.arithm(jobTasks(i).getEnd(), "<=", jobTasks(j).getStart()).post()
            // disjunctions +=  chocoModel.arithm(jobTasks(i).getEnd(), "=", jobTasks(j).getStart())
          } else if (a == aa && isSelfConcurrent(a)) {
            chocoModel
              .arithm(jobTasks(i).getStart(), "<=", jobTasks(j).getStart())
              .post()
          }
        }
      }
    }
    // -----/
    // buffers
    // for (
    //   (tuple, i) <- sdfAndSchedulers.sdfApplications.sdfMessages.zipWithIndex;
    //   (src, dst, _, size, p, c, initTokens) = tuple;
    //   (srcJob, j) <- jobsAndActors.zipWithIndex.filter((job, _) => job._1 == src);
    //   (srcA, srcQ) = srcJob;
    //   (dstJob, k) <- jobsAndActors.zipWithIndex.filter((job, _) => job._1 == dst);
    //   (dstA, dstQ) = dstJob
    // ) {
    //   chocoModel.ifThen(
    //     chocoModel.arithm(
    //       jobTasks(j).getEnd(),
    //       "<=",
    //       jobTasks(k).getStart()
    //     ),
    //     chocoModel.arithm(
    //       maxBufferTokens(i),
    //       ">=",
    //       initTokens + p * srcQ - c * (dstQ - 1)
    //     )
    //   )
    // }
    // -----/
    // throughput
    for ((a, i) <- actors.zipWithIndex) {
      val firstJob = jobsAndActors.indexWhere((aa, q) => a == aa && q == 1)
      val nextCycleJob =
        jobsAndActors.indexWhere((aa, q) => a == aa && q == maxRepetitionsPerActors(i))
      chocoModel
        .arithm(
          jobTasks(nextCycleJob).getEnd(),
          "-",
          jobTasks(firstJob).getStart(),
          "<=",
          invThroughputs(i)
        )
        .post()
      // chocoModel
      //   .scalar(
      //     Array(jobStartTime(nextCycleJob), duration(i), jobStartTime(firstJob)),
      //     Array(1, 1, -1),
      //     "<=",
      //     invThroughputs(i)
      //   )
      //   .post()
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
          .arithm(
            jobTasks(lastJob).getEnd(),
            "-",
            jobTasks(firstJob).getStart(),
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
            .arithm(
              jobTasks(lastJob).getEnd(),
              "-",
              jobTasks(firstJob).getStart(),
              "<=",
              invThroughputs(i)
            )
        )
      }
    }
    chocoModel.post(
      new Constraint(
        "SDFJobPropagator",
        SDFJobPropagator(
          jobTasks,
          (j) => memoryMappingModule.processesMemoryMapping(actors.indexOf(jobsAndActors(j)._1)),
          (i) =>
            (j) =>
              transmissionDelay(actors.indexOf(jobsAndActors(i)._1))(
                actors.indexOf(jobsAndActors(j)._1)
              ),
          (i) =>
            (j) =>
              sdfAndSchedulers.sdfApplications
                .firingsPrecedenceGraph.get(jobsAndActors(j))
                .pathTo(sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j)))
                .isDefined
        )
      )
    )
    // throughput
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
        chocoModel.arithm(jobTasks(i).getEnd(), "<=", jobTasks(j).getStart())
      )
    chocoModel.and(constraints: _*)
  }

}

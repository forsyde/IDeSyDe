package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import scala.collection.mutable.HashMap
// import breeze.linalg._
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.solver.constraints.`extension`.Tuples
import idesyde.utils.CoreUtils.wfor
import org.chocosolver.solver.Model
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsModule
import idesyde.identification.choco.models.TileAsyncInterconnectCommsModule
import scala.collection.mutable.Buffer
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore

class SDFSchedulingAnalysisModule2(
    val chocoModel: Model,
    val sdfAndSchedulers: SDFToTiledMultiCore,
    val memoryMappingModule: SingleProcessSingleMessageMemoryConstraintsModule,
    val tileAsyncModule: TileAsyncInterconnectCommsModule,
    val timeFactor: Long = 1L
) extends ChocoModelMixin() {

  private val actors = sdfAndSchedulers.sdfApplications.actorsIdentifiers
  val jobsAndActors =
    sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.nodes
      .map(v => v.value)
      .toVector
  private val schedulers = sdfAndSchedulers.platform.runtimes.schedulers
  private val actorDuration =
    sdfAndSchedulers.wcets.map(ws => ws.map(w => w * timeFactor).map(_.ceil.intValue))

  private val maxRepetitionsPerActors     = sdfAndSchedulers.sdfApplications.sdfRepetitionVectors
  private def isSelfConcurrent(a: String) = sdfAndSchedulers.sdfApplications.isSelfConcurrent(a)

  val slotRange                       = (0 until maxRepetitionsPerActors.sum).toVector
  private val maximumTokensPerChannel = sdfAndSchedulers.sdfApplications.pessimisticTokensPerChannel

  private val maxLength = schedulers.zipWithIndex
    .map((_, p) => {
      actors.zipWithIndex
        .map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai))
        .sum
    })
    .max + tileAsyncModule.messageTravelDuration.flatten.flatten.map(_.getUB()).sum

  val invThroughputs = actors.zipWithIndex
    .map((a, i) =>
      chocoModel.intVar(
        s"invTh($a)",
        actorDuration(i).filter(_ >= 0).minOption.getOrElse(0),
        maxLength,
        true
      )
    )
    .toArray

  val maxPath = schedulers
    .map(s =>
      jobsAndActors
        .map((aa, qq) =>
          chocoModel.intVar(
            s"maxPath($s, $aa, $qq)",
            0,
            maxLength,
            true
          )
        )
        .toArray
    )
    .toArray

  // val jobCycleLength =
  //   jobsAndActors
  //     .map((a, q) =>
  //       val i = actors.indexOf(a)
  //       chocoModel.intVar(
  //         s"jobCycleLength($a, $q)",
  //         0,
  //         maxThroughput,
  //         true
  //       )
  //     )
  //     .toArray

  val jobOrder =
    jobsAndActors
      .map((a, q) =>
        chocoModel.intVar(
          s"jobOrder($a, $q)",
          0,
          jobsAndActors.size - 1,
          false
        )
      )
      .toArray

  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      invThroughputs.map(_.getLB()).max,
      maxLength,
      true
    )

  val duration = actors.zipWithIndex
    .map((a, i) =>
      chocoModel.intVar(
        s"dur($a)",
        actorDuration(i).toArray
      )
    )
    .toArray

  val transmissionDelay =
    sdfAndSchedulers.sdfApplications.actorsIdentifiers.zipWithIndex.map((a, i) =>
      sdfAndSchedulers.sdfApplications.actorsIdentifiers.zipWithIndex
        .map((aa, j) => {
          val messageTimesIdx =
            sdfAndSchedulers.sdfApplications.sdfMessages
              .indexWhere((src, dst, _, _, _, _, _) => src == a && dst == aa)
          val maxMessageTime =
            if (messageTimesIdx > -1) then
              chocoModel.max(
                s"maxMessageTime($a, $aa)",
                tileAsyncModule.messageTravelDuration(messageTimesIdx).flatten
              )
            else chocoModel.intVar(0)
          maxMessageTime
        })
    )

  // val maxBufferTokens = sdfAndSchedulers.sdfApplications.sdfMessages.zipWithIndex.map((tuple, i) =>
  //   val (src, dst, _, size, p, c, tok) = tuple
  //   chocoModel.intVar(
  //     s"maxBufferTokens(${tuple._1}, ${tuple._2})",
  //     0,
  //     tok + sdfAndSchedulers.sdfApplications.sdfRepetitionVectors(actors.indexOf(src)) * p,
  //     true
  //   )
  // )

  val mappedJobsPerElement = schedulers.zipWithIndex.map((p, i) =>
    chocoModel.count(
      s"mappedJobsPerElement($p)",
      i,
      jobsAndActors.map((a, _) => memoryMappingModule.processesMemoryMapping(actors.indexOf(a))): _*
    )
  )

  val numMappedElements = chocoModel.intVar("numMappedElements", 1, schedulers.size, false)

  def jobMapping(jobi: Int) =
    memoryMappingModule.processesMemoryMapping(actors.indexOf(jobsAndActors(jobi)._1))

  def postSDFTimingAnalysis(): Unit = {
    chocoModel.nValues(memoryMappingModule.processesMemoryMapping, numMappedElements).post()
    chocoModel.count(0, jobOrder, numMappedElements).post()
    // --- general duration constraints
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", j),
        chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
      )
      for ((job, k) <- jobsAndActors.zipWithIndex; if job._1 == a) {
        chocoModel.ifThen(
          chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", j),
          chocoModel.arithm(jobOrder(k), "<", mappedJobsPerElement(j))
        )
      }
    }
    // -------------- next and ordering parts
    for ((p, j) <- schedulers.zipWithIndex) {
      chocoModel
        .allDifferentUnderCondition(
          jobOrder,
          (x) => jobMapping(jobOrder.indexOf(x)).isInstantiatedTo(j),
          false
        )
        .post()
    }
    // -- nexts are only valid when they are mapped in the same PE
    // -- must make a path
    for ((s, p) <- schedulers.zipWithIndex) {
      for (
        (u, i) <- jobsAndActors.zipWithIndex;
        (dst, q) = u;
        dstIdx   = actors.indexOf(dst)
      ) {
        val maxPrev = chocoModel.intVar(
          s"maxPrev($s, $dst, $q)",
          0,
          maxLength,
          true
        )
        val maxDep = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
          .get(u)
          .diPredecessors
          .map(_.value)
          .map((prevAct, prevQ) =>
            val prevIdx = jobsAndActors.indexOf((prevAct, prevQ))
            maxPath(p)(prevIdx)
              .add(transmissionDelay(actors.indexOf(prevAct))(actors.indexOf(dst)))
              .intVar()
          )
          .toArray
        chocoModel.ifThen(
          chocoModel.and(
            chocoModel.arithm(memoryMappingModule.processesMemoryMapping(dstIdx), "=", p),
            chocoModel.arithm(jobOrder(i), "=", 0)
          ),
          chocoModel.arithm(maxPrev, "=", 0)
        )
        for (
          (v, j) <- jobsAndActors.zipWithIndex;
          if i != j
        ) {
          chocoModel.ifThen(
            chocoModel.and(
              chocoModel.arithm(jobMapping(i), "=", jobMapping(j)),
              chocoModel.arithm(jobOrder(i), "=", jobOrder(j), "+", 1)
            ),
            chocoModel.arithm(maxPrev, "=", maxPath(p)(j))
          )
        }
        chocoModel
          .arithm(
            maxPath(p)(i),
            "=",
            duration(dstIdx),
            "+",
            chocoModel.max(s"maxPath($s, $dst, $q)", maxDep :+ maxPrev)
          )
          .post()
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
    // println(sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph)
    // println(sdfAndSchedulers.sdfApplications.firingsPrecedenceWithExtraStepGraph)
    for (
      (s, p)       <- schedulers.zipWithIndex;
      ((a, qi), i) <- jobsAndActors.zipWithIndex;
      aIdx = actors.indexOf(a)
    ) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(aIdx), "=", p),
        chocoModel
          .arithm(invThroughputs(aIdx), ">=", maxPath(p)(i))
      )
    }
    for (
      (a, aIdx) <- actors.zipWithIndex;
      predV <- sdfAndSchedulers.sdfApplications.firingsPrecedenceWithExtraStepGraph
        .get((a, maxRepetitionsPerActors(aIdx) + 1))
        .diPredecessors;
      (aa, qq) = predV.value;
      if a != aa && qq == maxRepetitionsPerActors(actors.indexOf(aa))
    ) {
      chocoModel
        .arithm(
          invThroughputs(aIdx),
          ">=",
          invThroughputs(actors.indexOf(aa))
        )
        .post()
      // val lastJob =
      //   jobsAndActors.indexWhere(_ == aa && _ == qq)
      // for ((s, p) <- schedulers.zipWithIndex) {
      // }
    }
    for ((a, i) <- actors.zipWithIndex; (aa, j) <- actors.zipWithIndex; if a != aa) {
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
    }
    // chocoModel.post(
    //   new Constraint(
    //     "SDFJobPropagator",
    //     SDFJobPropagator(
    //       jobTasks,
    //       (i) => jobOrder(i),
    //       (j) => memoryMappingModule.processesMemoryMapping(actors.indexOf(jobsAndActors(j)._1)),
    //       (i) =>
    //         (j) =>
    //           transmissionDelay(actors.indexOf(jobsAndActors(i)._1))(
    //             actors.indexOf(jobsAndActors(j)._1)
    //           ),
    //       (i) =>
    //         (j) =>
    //           sdfAndSchedulers.sdfApplications
    //             .firingsPrecedenceGraph.get(jobsAndActors(j))
    //             .pathTo(sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j)))
    //             .isDefined
    //     )
    //   )
    // )
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
      aIdx     = sdfAndSchedulers.sdfApplications.actorsIdentifiers.indexOf(a);
      aaIdx    = sdfAndSchedulers.sdfApplications.actorsIdentifiers.indexOf(aa)
    )
      yield chocoModel.or(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(aIdx), "!=", scheduler),
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(aaIdx), "!=", scheduler),
        chocoModel.arithm(jobOrder(i), "<", jobOrder(j))
      )
    chocoModel.and(constraints: _*)
  }

}

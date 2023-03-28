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
import idesyde.utils.HasUtils
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
) extends ChocoModelMixin() with HasUtils {

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
        actorDuration(i).filter(_ > 0).toArray
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
      if (actorDuration(i)(j) < 0) {
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "!=", j).post()
      } else {
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
    // make sure that actors in the same element follow the precedences
    for (
        ((ai, qi), i) <- jobsAndActors.zipWithIndex;
        (aj, qj) <- sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get((ai, qi)).outNeighbors.map(_.value);
        j = jobsAndActors.indexOf((aj, qj));
        aix = actors.indexOf(ai); 
        ajx = actors.indexOf(aj)
      ) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(aix), "=", memoryMappingModule.processesMemoryMapping(ajx)),
        chocoModel.arithm(jobOrder(i), "<", jobOrder(j))
      )
    }

    // -- nexts are only valid when they are mapped in the same PE
    // -- must make a path
    // for ((u, i) <- jobsAndActors.zipWithIndex; (v, j) <- jobsAndActors.zipWithIndex) {
    //   if (i == j) {
    //     chocoModel.arithm(maxPath(i)(j), "=", duration(actors.indexOf(u._1))).post()
    //   } else if (
    //     sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
    //       .get(u)
    //       .isDirectPredecessorOf(sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v))
    //   ) {
    //     chocoModel
    //       .sum(
    //         Array(
    //           duration(actors.indexOf(u._1)),
    //           transmissionDelay(actors.indexOf(u._1))(actors.indexOf(v._1)),
    //           duration(actors.indexOf(v._1))
    //         ),
    //         "=",
    //         maxPath(i)(j)
    //       )
    //       .post()
    //   } else {
    //     val maxSucc = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
    //       .get(u)
    //       .diSuccessors
    //       .map(_.value)
    //       .map((wa, wq) =>
    //         val k = jobsAndActors.indexOf((wa, wq))
    //         maxPath(k)(j)
    //           .add(transmissionDelay(actors.indexOf(u._1))(actors.indexOf(wa)))
    //           .intVar()
    //       )
    //       .toArray
    //     val maxNext = chocoModel.intVar(
    //       s"maxNext($u, $v)",
    //       0,
    //       maxLength,
    //       true
    //     )
    //     for ((w, k) <- jobsAndActors.zipWithIndex; if i != k) {
    //       chocoModel.ifThen(
    //         chocoModel.and(
    //           chocoModel.arithm(jobMapping(k), "=", jobMapping(i)),
    //           chocoModel.arithm(jobOrder(k), "=", jobOrder(i), "+", 1)
    //         ),
    //         chocoModel.arithm(maxNext, "=", maxPath(k)(j))
    //       )
    //     }
    //     for ((s, p) <- schedulers.zipWithIndex) {
    //       chocoModel.ifThen(
    //         chocoModel.and(
    //           chocoModel.arithm(jobMapping(i), "=", p),
    //           chocoModel.arithm(jobOrder(i), "=", mappedJobsPerElement(p), "-", 1)
    //         ),
    //         chocoModel.arithm(maxNext, "=", 0)
    //       )
    //     }
    //     chocoModel
    //       .arithm(
    //         maxPath(i)(j),
    //         "=",
    //         duration(actors.indexOf(u._1)),
    //         "+",
    //         chocoModel.max(s"maxPath($u, $v)", maxSucc :+ maxNext)
    //       )
    //       .post()
    //   }
    // }
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
    val thPropagator = StreamingJobsThroughputPropagator(
      jobsAndActors.size,
      isSuccessor,
      hasDataCycle,
      jobOrder,
      (0 until jobsAndActors.size).map(jobMapping(_)).toArray,
      jobsAndActors.map((a, _) => duration(actors.indexOf(a))).toArray,
      jobsAndActors
        .map((src, _) =>
          jobsAndActors
            .map((dst, _) => transmissionDelay(actors.indexOf(src))(actors.indexOf(dst)))
            .toArray
        )
        .toArray,
      jobsAndActors.map((a, _) => invThroughputs(actors.indexOf(a))).toArray
    )
    chocoModel.post(new Constraint("StreamingJobsThroughputPropagator", thPropagator))
    // for (
    //   ((ai, qi), i) <- jobsAndActors.zipWithIndex;
    //   ((aj, qj), j) <- jobsAndActors.zipWithIndex
    // ) {
    //   chocoModel.ifThen(
    //     chocoModel.arithm(jobMapping(i), "=", jobMapping(j)),
    //     chocoModel
    //       .arithm(invThroughputs(actors.indexOf(ai)), ">=", maxPath(i)(j))
    //   )
    // }
    // for (
    //   (a, aIdx) <- actors.zipWithIndex;
    //   predV <- sdfAndSchedulers.sdfApplications.firingsPrecedenceWithExtraStepGraph
    //     .get((a, maxRepetitionsPerActors(aIdx) + 1))
    //     .diPredecessors;
    //   (aa, qq) = predV.value;
    //   if a != aa && qq == maxRepetitionsPerActors(actors.indexOf(aa))
    // ) {
    //   val i = jobsAndActors.indexOf((a, 1))
    //   val j = jobsAndActors.indexOf((aa, qq))
    //   chocoModel
    //     .arithm(
    //       invThroughputs(aIdx),
    //       ">=",
    //       maxPath(i)(j),
    //       "+",
    //       transmissionDelay(actors.indexOf(aa))(actors.indexOf(a))
    //     )
    //     .post()
    //   // val lastJob =
    //   //   jobsAndActors.indexWhere(_ == aa && _ == qq)
    //   // for ((s, p) <- schedulers.zipWithIndex) {
    //   // }
    // }
    for ((a, i) <- actors.zipWithIndex; (aa, j) <- actors.zipWithIndex; if a != aa) {
      if (
        sdfAndSchedulers.sdfApplications.sdfGraph
          .get(a)
          .isPredecessorOf(sdfAndSchedulers.sdfApplications.sdfGraph.get(aa))
        || sdfAndSchedulers.sdfApplications.sdfGraph
          .get(aa)
          .isPredecessorOf(sdfAndSchedulers.sdfApplications.sdfGraph.get(a))
      ) {
        chocoModel
          .arithm(
            invThroughputs(i),
            "=",
            invThroughputs(j)
          )
          .post()
      } else {
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

  def isSuccessor(i: Int)(j: Int) = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
            .get(jobsAndActors(i))
            .isDirectPredecessorOf(
              sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j))
            )

  def hasDataCycle(i: Int)(j: Int) = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
            .get(jobsAndActors(i))
            .isPredecessorOf(
              sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j))
            ) && 
        sdfAndSchedulers.sdfApplications.firingsPrecedenceGraphWithCycles
            .get(jobsAndActors(j))
            .isPredecessorOf(
              sdfAndSchedulers.sdfApplications.firingsPrecedenceGraphWithCycles.get(jobsAndActors(i))
            ) 

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

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

  private val maxSingleCoreInterval = schedulers.zipWithIndex
    .map((_, p) => {
      actors.zipWithIndex
        .map((a, ai) => actorDuration(ai)(p) * (maxRepetitionsPerActors(ai) + 1))
        .sum
    })
    .max

  val invThroughputs: Array[IntVar] = actors.zipWithIndex
    .map((a, i) =>
      chocoModel.intVar(
        s"invTh($a)",
        actorDuration(i).filter(_ >= 0).minOption.getOrElse(0) * maxRepetitionsPerActors(i),
        maxSingleCoreInterval,
        true
      )
    )

  val procLenthgs: Array[IntVar] = schedulers.zipWithIndex
    .map((p, i) =>
      chocoModel.intVar(
        s"procLenthgs($p)",
        0,
        maxSingleCoreInterval,
        true
      )
    )

  val jobStartTime: Array[IntVar] =
    jobsAndActors
      .map((a, q) =>
        chocoModel.intVar(
          s"jobStartTime($a, $q)",
          0,
          maxSingleCoreInterval,
          true
        )
      )

  // val jobTimeMatrix: Array[Array[IntVar]] =
  //   jobsAndActors
  //     .map((a, q) =>
  //       jobsAndActors
  //         .map((aa, qq) =>
  //           chocoModel.intVar(
  //             s"jobTimeMatrix($a, $q, $aa, $qq)",
  //             0,
  //             maxSingleCoreInterval,
  //             true
  //           )
  //         )
  //     )

  val jobOrdering: Array[IntVar] = jobsAndActors
    .map((a, q) =>
      chocoModel.intVar(
        s"jobOrdering($a, $q)",
        1,
        jobsAndActors.size,
        false
      )
    )
    .toArray

  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      schedulers
        .map(p => actors.map(a => actorDuration(a)(p)).min)
        .min,
      maxSingleCoreInterval,
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

  // val mappedPerProcessingElement = schedulers.map(p => {
  //   chocoModel.count(
  //     s"mappedPerProcessingElement($p)",
  //     p,
  //     jobsAndActors.map((a, q) => memoryMappingModule.processesMemoryMapping(actors.indexOf(a))): _*
  //   )
  // })

  // val maxDistanceInProcessingElement = schedulers.map(p =>
  //   chocoModel.intVar(s"maxDistanceInProcessingElement($p)", 0, jobsAndActors.size - 1, true)
  // )

  // val firstJobInSchedule =
  //   schedulers.map(p =>
  //     chocoModel.intVar(s"firstJobInSchedule($p)", 0, jobsAndActors.size - 1, true)
  //   )

  // val lastJobInSchedule =
  //   schedulers.map(p =>
  //     chocoModel.intVar(s"lastJobInSchedule($p)", 0, jobsAndActors.size - 1, true)
  //   )

  def postSDFTimingAnalysis(): Unit = {
    // println(sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.toString())
    val zeroes = chocoModel.min("zeroes", jobStartTime: _*)
    chocoModel.arithm(zeroes, "=", 0).post()
    // chocoModel.sum(mappedPerProcessingElement, "=", jobsAndActors.size).post()
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", p),
        chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
      )
    }
    // --- different ordering within tiles section
    val jobOrderingsToActorsIdx =
      jobOrdering.zipWithIndex.map((x, i) => x -> actors.indexOf(jobsAndActors(i)._1)).toMap
    for ((p, i) <- schedulers.zipWithIndex) {
      chocoModel
        .allDifferentUnderCondition(
          jobOrdering,
          (x) => {
            val actorIdx = jobOrderingsToActorsIdx(x)
            memoryMappingModule.processesMemoryMapping(actorIdx).isInstantiatedTo(p)
          },
          false
        )
        .post()
    }
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      (a, q) = v
    ) {
      val predecessors = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
        .get(v)
        .diPredecessors
      val immediateFollows =
        if (predecessors.size > 0) then
          chocoModel
            .max(
              s"maximumOrderFor($i)",
              predecessors
                .map(_.value)
                .map(jobsAndActors.indexOf(_))
                .map(jobOrdering(_))
                .toArray
            )
            .add(1)
            .eq(jobOrdering(i))
            .decompose()
        else jobOrdering(i).eq(1).decompose()
      val hasAPredecessor = chocoModel.or(
        jobsAndActors.zipWithIndex
          .filter((vv, k) => k != i)
          .map((vv, k) =>
            chocoModel.and(
              memoryMappingModule
                .processesMemoryMapping(actors.indexOf(a))
                .eq(memoryMappingModule.processesMemoryMapping(actors.indexOf(vv._1)))
                .decompose(),
              jobOrdering(k).sub(1).eq(jobOrdering(i)).decompose()
            )
          ): _*
      )
      chocoModel
        .or(
          hasAPredecessor,
          immediateFollows
        )
        .post()
    }
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      (a, q) = v;
      (vv, j) <- jobsAndActors.zipWithIndex;
      (aa, qq) = vv
      if i != j
    ) {
      val aIdx  = sdfAndSchedulers.sdfApplications.actorsSet.indexOf(a)
      val aaIdx = sdfAndSchedulers.sdfApplications.actorsSet.indexOf(aa)
      chocoModel.ifThen(
        chocoModel.arithm(
          memoryMappingModule.processesMemoryMapping(aIdx),
          "=",
          memoryMappingModule.processesMemoryMapping(aaIdx)
        ),
        chocoModel.arithm(jobOrdering(i), "!=", jobOrdering(j))
      )
      // symmetry reduction based on topology
      // if (
      //   sdfAndSchedulers.sdfApplications.topologicalAndHeavyJobOrderingWithExtra.indexOf(
      //     v
      //   ) < sdfAndSchedulers.sdfApplications.topologicalAndHeavyJobOrderingWithExtra.indexOf(vv)
      // ) {
      //   chocoModel.ifThen(
      //     chocoModel.arithm(
      //       memoryMappingModule.processesMemoryMapping(aIdx),
      //       "=",
      //       memoryMappingModule.processesMemoryMapping(aaIdx)
      //     ),
      //     chocoModel.arithm(jobOrdering(i), "<", jobOrdering(j))
      //   )
      // }
    }
    // timing section
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      (a, q) = v;
      dst <- sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph
        .get(v)
        .diSuccessors;
      (aa, qq) = dst.value;
      j        = jobsAndActors.indexOf(dst.value)
    ) {
      chocoModel.arithm(jobOrdering(i), "<", jobOrdering(j)).post()
      if (a != aa) {
        // chocoModel
        //   .arithm(jobStartTime(j), ">=", jobStartTime(i), "+", duration(actors.indexOf(a)))
        //   .post()
        val messageTimesIdx =
          sdfAndSchedulers.sdfApplications.sdfMessages.indexWhere((src, dst, _, _, _, _, _) =>
            src == a && dst == aa
          )
        for (tileSrc <- schedulers; tileDst <- schedulers) {
          chocoModel.ifThen(
            chocoModel.and(
              chocoModel.arithm(
                memoryMappingModule.processesMemoryMapping(actors.indexOf(a)),
                "=",
                tileSrc
              ),
              chocoModel.arithm(
                memoryMappingModule.processesMemoryMapping(actors.indexOf(aa)),
                "=",
                tileDst
              )
            ),
            chocoModel.scalar(
              Array(
                tileAsyncModule.messageTravelDuration(messageTimesIdx)(tileSrc)(tileDst),
                jobStartTime(i),
                duration(actors.indexOf(a))
              ),
              Array(1, 1, 1),
              "<=",
              jobStartTime(j)
            )
          )
        }
      } else if (a == aa && q < qq && !isSelfConcurrent(actors.indexOf(a))) {
        chocoModel
          .arithm(jobStartTime(j), ">=", jobStartTime(i), "+", duration(actors.indexOf(a)))
          .post()
      } else if (a == aa && q < qq && isSelfConcurrent(actors.indexOf(a))) {
        chocoModel.arithm(jobStartTime(j), ">=", jobStartTime(i)).post()
      }
      // this search space reduction is only valid because the number of tokens allocated are always
      // conservative
    }
    for (
      (v, i)  <- jobsAndActors.zipWithIndex;
      (vv, j) <- jobsAndActors.zipWithIndex;
      src = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v);
      dst = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(vv);
      a   = actors.indexOf(v._1);
      aa  = actors.indexOf(vv._1);
      if i != j
    ) {
      // chocoModel.ifOnlyIf(
      //   chocoModel.arithm(
      //     jobOrdering(j),
      //     ">",
      //     jobOrdering(i)
      //   ),
      //   chocoModel.arithm(
      //     jobStartTime(j),
      //     ">",
      //     jobStartTime(i)
      //   )
      // )
      chocoModel.ifThen(
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(a),
            "=",
            memoryMappingModule.processesMemoryMapping(aa)
          ),
          chocoModel.arithm(
            jobOrdering(j),
            ">",
            jobOrdering(i)
          )
        ),
        chocoModel.arithm(
          jobStartTime(j),
          ">=",
          jobStartTime(i),
          "+",
          duration(a)
        )
      )
    }
    for (
      (a, i)  <- actors.zipWithIndex;
      (aa, j) <- actors.zipWithIndex;
      if a != aa;
      firstAIdx = jobsAndActors.indexWhere((ja, jq) => ja == a && jq == 1);
      lastAAIdx = jobsAndActors
        .indexWhere((ja, jq) => ja == aa && jq == maxRepetitionsPerActors(j));
      (p, k) <- schedulers.zipWithIndex
    ) {
      chocoModel.ifThen(
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(i),
            "=",
            p
          ),
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(j),
            "=",
            p
          )
        ),
        chocoModel.scalar(
          Array(
            jobStartTime(firstAIdx),
            jobStartTime(lastAAIdx),
            duration(j)
          ),
          Array(-1, 1, 1),
          "<=",
          procLenthgs(k)
        )
      )
    }
    chocoModel.post(
      new Constraint(
        "OrderedJobUpperBoundPropagator",
        OrderedJobUpperBoundPropagator(
          jobsAndActors.map((a, _) =>
            memoryMappingModule.processesMemoryMapping(actors.indexOf(a))
          ),
          jobOrdering,
          jobStartTime,
          jobsAndActors.map((a, _) => duration(actors.indexOf(a)))
        )
      )
    )
    // throughput
    for ((a, i) <- actors.zipWithIndex) {
      val firstJob = jobsAndActors.indexWhere((aa, q) => a == aa && q == 1)
      val nextCycleJob =
        jobsAndActors.indexWhere((aa, q) => a == aa && q == maxRepetitionsPerActors(a))
      chocoModel
        .arithm(jobStartTime(nextCycleJob), "-", jobStartTime(firstJob), "<=", invThroughputs(i))
        .post()
      for ((p, k) <- schedulers.zipWithIndex) {
        chocoModel.ifThen(
          memoryMappingModule.processesMemoryMapping(i).eq(p).decompose(),
          invThroughputs(i).ge(procLenthgs(k)).decompose()
        )
      }
      for (
        predV <- sdfAndSchedulers.sdfApplications.firingsPrecedenceWithExtraStepGraph
          .get((a, maxRepetitionsPerActors(i) + 1))
          .diPredecessors;
        (aa, qq) = predV.value;
        if a != aa && qq <= maxRepetitionsPerActors(actors.indexOf(aa))
      ) {
        val nextOtherCycleJob =
          jobsAndActors.indexWhere((aaa, q) => aa == aaa && q == qq)
        chocoModel
          .arithm(
            jobStartTime(nextOtherCycleJob),
            "-",
            jobStartTime(firstJob),
            "<=",
            invThroughputs(i)
          )
          .post()
      }
      // chocoModel.scalar(
      //   Array(
      //     tileAsyncModule.messageTravelDuration(messageTimesIdx)(tileSrc)(tileDst),
      //     jobStartTime(i),
      //     duration(actors.indexOf(a))
      //   ),
      //   Array(1, 1, 1),
      //   "=",
      //   invThroughputs(i)
      // )
      // invThroughputs(i).eq()
      //   chocoModel
      //     .scalar(
      //       memoryMappingModule.processesMemoryMapping,
      //       actorDuration.zipWithIndex.map((w, j) =>
      //         w(i) * sdfAndSchedulers.sdfApplications.sdfRepetitionVectors(j)
      //       ),
      //       "<=",
      //       invThroughputs(i)
      //     )
      //     .post()
    }
    chocoModel.max(globalInvThroughput, invThroughputs).post()
    // chocoModel.post(
    //   new Constraint(
    //     "MultiCoreJobThroughputPropagator",
    //     MultiCoreJobThroughputPropagator(
    //       jobsAndActors.map((a, _) =>
    //         memoryMappingModule.processesMemoryMapping(actors.indexOf(a))
    //       ),
    //       jobStartTime,
    //       jobsAndActors.map((a, _) => duration(actors.indexOf(a))),
    //       procLenthgs
    //     )
    //   )
    // )

  }

}

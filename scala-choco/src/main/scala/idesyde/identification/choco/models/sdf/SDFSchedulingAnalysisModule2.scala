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
  private val channels: Array[Int] = sdfAndSchedulers.sdfApplications.channelsSet
  private val messages: Array[Int] =
    sdfAndSchedulers.sdfApplications.sdfMessages.zipWithIndex.map((_, i) => i)
  private val schedulers: Array[Int] = sdfAndSchedulers.platform.schedulerSet
  private val balanceMatrix: Array[Array[Int]] =
    sdfAndSchedulers.sdfApplications.balanceMatrices.head
  private val initialTokens: Array[Int] = sdfAndSchedulers.sdfApplications.initialTokens
  private val actorDuration: Array[Array[Int]] =
    sdfAndSchedulers.wcets.map(ws => ws.map(w => w * timeFactor).map(_.ceil.intValue))

  private val maxRepetitionsPerActors     = sdfAndSchedulers.sdfApplications.sdfRepetitionVectors
  private def isSelfConcurrent(aIdx: Int) = sdfAndSchedulers.sdfApplications.isSelfConcurrent(aIdx)

  val slotRange                       = (0 until maxRepetitionsPerActors.sum).toArray
  private val maximumTokensPerChannel = sdfAndSchedulers.sdfApplications.pessimisticTokensPerChannel

  private val maxSingleCoreInterval = schedulers.zipWithIndex
    .map((_, p) => {
      actors.zipWithIndex
        .map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai))
        .sum
    })
    .max

  val invThroughputs: Array[IntVar] = schedulers
    .map(p =>
      chocoModel.intVar(
        s"invTh($p)",
        0,
        maxSingleCoreInterval,
        true
      )
    )

  // val jobsInvThroughputs: Array[IntVar] = jobsAndActors.map((a, q) =>
  //   chocoModel.intVar(
  //     s"jobInvThroughput($a, $q)",
  //     actorDuration(actors.indexOf(a)).filter(_ >= 0).minOption.getOrElse(0),
  //     maxSingleCoreInterval,
  //     true
  //   )
  // )

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

  def postSDFTimingAnalysis(): Unit = {
    val zeroes = chocoModel.min("zeroes", jobStartTime: _*)
    // val zeroesMatrix = chocoModel.min("zeroesMatrix", jobTimeMatrix.flatten: _*)
    chocoModel.arithm(zeroes, "=", 0).post()
    // chocoModel.arithm(zeroesMatrix, "=", 0).post()
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      chocoModel.ifThen(
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(i), "=", p),
        chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
      )
    }
    // --- different ordering within tiles section
    val jobOrderingsToActorsIdx =
      jobOrdering.zipWithIndex.map((x, i) => x -> actors.indexOf(jobsAndActors(i)._1)).toMap
    for (p <- schedulers) {
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
      }
    }
    // timing section
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      (a, q) = v;
      dst <- sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v).diSuccessors;
      (aa, qq) = dst.value;
      j        = jobsAndActors.indexOf(dst.value)
    ) {
      chocoModel.arithm(jobOrdering(i), "<", jobOrdering(j)).post()
      if (a != aa) {
        chocoModel
          .arithm(jobStartTime(j), ">=", jobStartTime(i), "+", duration(actors.indexOf(a)))
          .post()
        // jobStartTime(j).ge(jobStartTime(i).add(duration(a))).post()
        val messageTimesIdx =
          sdfAndSchedulers.sdfApplications.sdfMessages.indexWhere((src, dst, _, _, _, _, _) =>
            src == a && dst == aa
          )
        // val maxMessageDuration = chocoModel.max(
        //   s"maxMessageDuration($messageTimesIdx)",
        //   tileAsyncModule.messageTravelDuration(messageTimesIdx).flatten
        // )
        // chocoModel
        //   .scalar(
        //     Array(
        //       maxMessageDuration,
        //       jobStartTime(i),
        //       duration(actors.indexOf(a))
        //     ),
        //     Array(1, 1, 1),
        //     "=",
        //     jobTimeMatrix(i)(j)
        //   )
        //   .post()
        for (tileSrc <- schedulers; tileDst <- schedulers; if tileSrc != tileDst) {
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
        // chocoModel
        //   .arithm(jobTimeMatrix(i)(j), "=", jobStartTime(i), "+", duration(actors.indexOf(a)))
        //   .post()
      } else if (a == aa && q < qq && isSelfConcurrent(actors.indexOf(a))) {
        chocoModel.arithm(jobStartTime(j), ">=", jobStartTime(i)).post()
        // chocoModel
        //   .arithm(jobTimeMatrix(i)(j), "=", jobStartTime(i))
        //   .post()
      }
      // this search space reduction is only valid because the number of tokens allocated are always
      // conservative
      for (
        dstdst <- dst.diSuccessors;
        (aaa, qqq) = dstdst.value;
        k        = jobsAndActors.indexOf(dstdst.value)
      ) {
        chocoModel.not(
          chocoModel.and(
            chocoModel.arithm(memoryMappingModule.processesMemoryMapping(actors.indexOf(a)), "=", memoryMappingModule.processesMemoryMapping(actors.indexOf(aaa))),
            chocoModel.arithm(memoryMappingModule.processesMemoryMapping(actors.indexOf(a)), "!=", memoryMappingModule.processesMemoryMapping(actors.indexOf(aa))),
            chocoModel.arithm(memoryMappingModule.processesMemoryMapping(actors.indexOf(aa)), "!=", memoryMappingModule.processesMemoryMapping(actors.indexOf(aaa)))
          )
        ).post()
      }
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
      // if (src.pathTo(dst).isEmpty && dst.pathTo(src).isEmpty) {
      //   chocoModel.ifThenElse(
      //     chocoModel.arithm(
      //       jobOrdering(j),
      //       ">",
      //       jobOrdering(i)
      //     ),
      //     chocoModel.arithm(
      //       jobTimeMatrix(i)(j),
      //       "=",
      //       jobStartTime(i),
      //       "+",
      //       duration(a)
      //     ),
      //     chocoModel.arithm(
      //       jobTimeMatrix(i)(j),
      //       "=",
      //       0
      //     )
      //   )
      // }
    }
    // ensure all first jobs start ASAP
    // for ((v, i) <- jobsAndActors.zipWithIndex) {
    //   chocoModel.arithm(jobTimeMatrix(i)(i), "=", 0).post()
    //   chocoModel.max(jobStartTime(i), jobTimeMatrix.map(v => v(i))).post()
    // }
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
    chocoModel.max(globalInvThroughput, invThroughputs).post()
    chocoModel.post(
      new Constraint(
        "MultiCoreJobThroughputPropagator",
        MultiCoreJobThroughputPropagator(
          jobsAndActors.map((a, _) =>
            memoryMappingModule.processesMemoryMapping(actors.indexOf(a))
          ),
          jobStartTime,
          jobsAndActors.map((a, _) => duration(actors.indexOf(a))),
          invThroughputs
        )
      )
    )

  }

}

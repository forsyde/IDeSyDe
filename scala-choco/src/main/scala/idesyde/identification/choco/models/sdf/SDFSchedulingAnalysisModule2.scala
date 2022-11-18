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

  val invThroughputs: Array[IntVar] = schedulers
    .map(p =>
      chocoModel.intVar(
        s"invTh($p)",
        0,
        actors.zipWithIndex
          .map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai))
          .sum + tileAsyncModule.messageTravelDuration
          .flatMap(cVec => cVec.map(pOtherVec => pOtherVec(p).getUB()))
          .sum,
        true
      )
    )
  // private val slotThroughput: Array[Array[IntVar]] = schedulers
  //   .map(p =>
  //     slotRange.map(s =>
  //       chocoModel.intVar(s"slotThroughput($p, $s)", 0, invThroughputs(p).getUB(), true)
  //     )
  //   )
  val jobStartTime: Array[IntVar] =
    jobsAndActors
      .map((a, q) =>
        chocoModel.intVar(
          s"finishTime($a, $q)",
          0,
          invThroughputs.map(_.getUB()).max,
          true
        )
      )
      .toArray
  val jobOrdering: Array[IntVar] = jobsAndActors
    .map((a, q) =>
      chocoModel.intVar(
        s"ordering($a, $q)",
        1,
        jobsAndActors.size,
        true
      )
    )
    .toArray
  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      schedulers
        .map(p => actors.map(a => actorDuration(a)(p)).min)
        .min,
      schedulers
        .map(p => invThroughputs(p).getUB())
        .max,
      true
    )

  val duration = actors.zipWithIndex.map((a, i) =>
    chocoModel.intVar(s"dur($a)", actorDuration(i).min, actorDuration(i).max, true)
  )

  def postSDFTimingAnalysis(): Unit = {
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
      // for (
      //   (v, i) <- jobsAndActors.zipWithIndex;
      //   (a, q) = v;
      //   (vv, j) <- jobsAndActors.zipWithIndex;
      //   (aa, qq) = vv
      //   if i != j
      // ) {
      //   val aIdx  = sdfAndSchedulers.sdfApplications.actorsSet.indexOf(a)
      //   val aaIdx = sdfAndSchedulers.sdfApplications.actorsSet.indexOf(aa)
      //   chocoModel.ifThen(
      //     chocoModel.arithm(
      //       memoryMappingModule.processesMemoryMapping(aIdx),
      //       "=",
      //       memoryMappingModule.processesMemoryMapping(aaIdx)
      //     ),
      //     chocoModel.arithm(jobOrdering(i), "!=", jobOrdering(j))
      //   )
      // }
    }
    for (
      (v, i) <- jobsAndActors.zipWithIndex;
      (a, q) = v;
      eOut <- sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v).outgoing;
      (aa, qq) = eOut._2.value;
      j        = jobsAndActors.indexOf(eOut._2.value)
    ) {
      if (a != aa) {
        val messageTimesIdx =
          sdfAndSchedulers.sdfApplications.sdfMessages.indexWhere((src, dst, _, _) =>
            src == a && dst == aa
          )
        val messageTimesSum = chocoModel.sum(
          s"sumOfMessages($a, $aa)",
          tileAsyncModule.messageTravelDuration(messageTimesIdx).flatten: _*
        )
        jobStartTime(j).ge(jobStartTime(i).add(duration(a)).add(messageTimesSum)).post()
      } else if (a == aa && q < qq && isSelfConcurrent(a)) {
        jobStartTime(j).ge(jobStartTime(i).add(duration(a))).post()
      } else if (a == aa && q < qq && !isSelfConcurrent(a)) {
        jobStartTime(j).ge(jobStartTime(i)).post()
      }
    }
    for (
      (v, i)  <- jobsAndActors.zipWithIndex;
      (vv, j) <- jobsAndActors.zipWithIndex;
      src = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(v);
      dst = sdfAndSchedulers.sdfApplications.firingsPrecedenceGraph.get(vv)
      if src.pathTo(dst).isEmpty && dst.pathTo(src).isEmpty;
      a  = actors.indexOf(v._1);
      aa = actors.indexOf(vv._1)
    ) {
      chocoModel.ifThen(
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(a),
            "=",
            memoryMappingModule.processesMemoryMapping(aa)
          ),
          chocoModel.arithm(
            jobOrdering(i),
            "<",
            jobOrdering(j)
          )
        ),
        jobStartTime(j).ge(jobStartTime(i).add(duration(a))).decompose()
      )
    }
    for (
      p       <- schedulers;
      (v, i)  <- jobsAndActors.zipWithIndex;
      (vv, j) <- jobsAndActors.zipWithIndex;
      a  = actors.indexOf(v._1);
      aa = actors.indexOf(vv._1)
    ) {
      chocoModel.ifThen(
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(actors.indexOf(a)),
            "=",
            p
          ),
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(actors.indexOf(aa)),
            "=",
            p
          )
        ),
        invThroughputs(p).ge(jobStartTime(j).add(duration(aa)).sub(jobStartTime(i))).decompose()
      )
      chocoModel.ifThen(
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(actors.indexOf(a)),
            "=",
            p
          ),
          chocoModel.arithm(
            memoryMappingModule.processesMemoryMapping(actors.indexOf(aa)),
            "=",
            p
          )
        ),
        invThroughputs(p).ge(jobStartTime(i).add(duration(a)).sub(jobStartTime(j))).decompose()
      )
    }
    // throughput
    chocoModel.max(globalInvThroughput, invThroughputs).post()

  }

}

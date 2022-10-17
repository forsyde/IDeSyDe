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

class SDFSASAnalysisModule(
    val chocoModel: Model,
    val sdfAndSchedulers: SDFToSchedTiledHW,
    val memoryMappingModule: SingleProcessSingleMessageMemoryConstraintsModule,
    val tileAsyncModule: TileAsyncInterconnectCommsModule,
    val maxSlots: Int,
    val timeFactor: Long = 1L
) extends ChocoModelMixin() {

  private val actors: Array[Int]     = sdfAndSchedulers.sdfApplications.actorsSet
  private val channels: Array[Int]   = sdfAndSchedulers.sdfApplications.channelsSet
  private val schedulers: Array[Int] = sdfAndSchedulers.platform.schedulerSet
  private val balanceMatrix: Array[Array[Int]] =
    sdfAndSchedulers.sdfApplications.balanceMatrices.head
  private val initialTokens: Array[Int] = sdfAndSchedulers.sdfApplications.initialTokens
  private val actorDuration: Array[Array[Int]] =
    sdfAndSchedulers.wcets.map(ws => ws.map(w => w * timeFactor).map(_.ceil.intValue))

  private val maxRepetitionsPerActors     = sdfAndSchedulers.sdfApplications.sdfRepetitionVectors
  private def isSelfConcurrent(aIdx: Int) = sdfAndSchedulers.sdfApplications.isSelfConcurrent(aIdx)

  private val slotRange               = (0 until maxSlots).toArray
  private val maximumTokensPerChannel = sdfAndSchedulers.sdfApplications.pessimisticTokensPerChannel

  val invThroughputs: Array[IntVar] = schedulers
    .map(p =>
      chocoModel.intVar(
        s"invTh($p)",
        0,
        actors.zipWithIndex.map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai)).sum,
        true
      )
    )
  // private val slotThroughput: Array[Array[IntVar]] = schedulers
  //   .map(p =>
  //     slotRange.map(s =>
  //       chocoModel.intVar(s"slotThroughput($p, $s)", 0, invThroughputs(p).getUB(), true)
  //     )
  //   )
  val slotFinishTime: Array[Array[IntVar]] = schedulers
    .map(p =>
      slotRange.map(s =>
        chocoModel.intVar(s"finishTime($p, $s)", 0, invThroughputs(p).getUB(), true)
      )
    )
  val slotStartTime: Array[Array[IntVar]] = schedulers
    .map(p =>
      slotRange.map(s =>
        chocoModel.intVar(s"startTime($p, $s)", 0, invThroughputs(p).getUB(), true)
      )
    )
  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      schedulers
        .map(p => actors.map(a => actorDuration(a)(p)).min)
        .min,
      schedulers
        .map(p =>
          actors.zipWithIndex.map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai)).sum
        )
        .max,
      true
    )
  val firingsInSlots: Array[Array[Array[IntVar]]] =
    actors.zipWithIndex.map((a, i) => {
      schedulers.zipWithIndex.map((p, j) => {
        slotRange
          .map(s =>
            chocoModel
              .intVar(
                s"fired($a,$p,$s)",
                0,
                maxRepetitionsPerActors(i),
                true
              )
          )
          .toArray
      })
    })

  val tokens: Array[Array[IntVar]] =
    channels.zipWithIndex.map((c, i) => {
      slotRange
        .map(s =>
          chocoModel
            .intVar(
              s"tokens($c,$s)",
              0,
              maximumTokensPerChannel(i),
              true
            )
        )
        .toArray
    })

  val duration = schedulers.map(p =>
    slotRange.map(s =>
      chocoModel.intVar(s"slotDur($p,$s)", 0, slotFinishTime.head.last.getUB(), true)
    )
  )

  def postOnlySAS(): Unit = {
    actors.zipWithIndex.foreach((a, ai) => {
      // disable self concurreny if necessary
      // if (!isSelfConcurrent(a)) {
      //   for (slot <- slotRange) {
      //     chocoModel.sum(schedulers.map(p => firingsInSlots(a)(p)(slot)), "<=", 1).post()
      //   }
      // }
      // set total number of firings
      chocoModel.sum(firingsInSlots(ai).flatten, "=", maxRepetitionsPerActors(ai)).post()
    })
    for (
      (s, sj) <- schedulers.zipWithIndex;
      slot    <- 0 until maxSlots
    ) {
      val onlyOneActor = Tuples(true)
      onlyOneActor.add(Array.fill(actors.size)(0))
      for ((a, ai) <- actors.zipWithIndex; q <- 1 to maxRepetitionsPerActors(ai)) {
        val vec = (Array.fill(ai)(0) :+ q) ++ Array.fill(actors.size - ai - 1)(0)
        onlyOneActor.add(vec)
      }
      chocoModel
        .table(actors.map(a => firingsInSlots(a)(sj)(slot)).toArray, onlyOneActor, "CT+")
        .post()
    }
  }

  def postSDFConsistencyConstraints(): Unit = {
    val consMatrix = balanceMatrix.map(m => m.map(v => if (v < 0) then v else 0))
    val prodMatrix = balanceMatrix.map(m => m.map(v => if (v > 0) then v else 0))
    // in the same slot
    for (s <- slotRange; (_, ci) <- channels.zipWithIndex) {
      val consumed = chocoModel.intVar(
        s"consumed($s, $ci)",
        -maximumTokensPerChannel(ci),
        0,
        true
      )
      val result = chocoModel.intVar(
        s"produced($s, $ci)",
        -maximumTokensPerChannel(ci),
        maximumTokensPerChannel(ci),
        true
      )
      val allFirings = actors.zipWithIndex
        .map((_, a) => schedulers.zipWithIndex.map((_, p) => firingsInSlots(a)(p)(s)))
        .flatten
      val allConFactors = actors.zipWithIndex
        .map((_, a) => schedulers.zipWithIndex.map((_, p) => consMatrix(ci)(a)))
        .flatten
      val allFactors = actors.zipWithIndex
        .map((_, a) => schedulers.zipWithIndex.map((_, p) => balanceMatrix(ci)(a)))
        .flatten
      chocoModel.scalar(allFirings, allConFactors, "=", consumed).post()
      chocoModel.scalar(allFirings, allFactors, "=", result).post()
      if (s > 0) {
        chocoModel.arithm(tokens(ci)(s), "=", tokens(ci)(s - 1), "+", result).post()
        // tokens(ci)(s).eq(tokens(ci)(s - 1).add(result)).decompose().post()
        // chocoModel.arithm(0, "<", tokens(ci)(s - 1), "+", consumed).post()
        tokens(ci)(s - 1).add(consumed).ge(0).decompose().post()
      } else {
        tokens(ci)(s).eq(result.add(initialTokens(ci))).decompose().post()
        consumed.add(initialTokens(ci)).ge(0).decompose().post()
      }
      // tokensAfter(s)(c).eq(chocoModel.sum(s"fireSum($s, $c)", actors.zipWithIndex.map()))
    }
    // now maintain the tokens from one slot to another
  }

  def postSDFTimingAnalysisSAS(): Unit = {
    val maximumTokensProducedVal = maximumTokensPerChannel.max
    val consMat                  = balanceMatrix.map(bs => bs.map(b => if (b < 0) then b else 0))
    val prodMat                  = balanceMatrix.map(bs => bs.map(b => if (b > 0) then b else 0))
    postOnlySAS()
    postSDFConsistencyConstraints()
    // timings
    for (p <- schedulers) {
      chocoModel.sum(slotRange.map(s => duration(p)(s)), "<=", invThroughputs(p)).post()
      for (s <- slotRange) {
        val actorFirings = actors.map(a => firingsInSlots(a)(p)(s))
        //val duration = duration(p)(s)// chocoModel.intVar(s"slotDur($p,$s)", 0, slotFinishTime.head.last.getUB(), true)
        // val busyTime =
        //   chocoModel.intVar(s"busyTime($p,$s)", 0, slotFinishTime.head.last.getUB(), true)
        chocoModel
          .scalar(actorFirings, actors.map(a => actorDuration(a)(p)), "=", duration(p)(s))
          .post()
        chocoModel
          .arithm(slotFinishTime(p)(s), "=", duration(p)(s), "+", slotStartTime(p)(s))
          .post()
        // chocoModel.arithm(busyTime, ">=", duration(p)(s)).post()
        // slotFinishTime(p)(s).eq(duration.add(slotStartTime(p)(s))).decompose().post()
        if (s > 0) {
          chocoModel.arithm(slotStartTime(p)(s), ">=", slotFinishTime(p)(s - 1)).post()
          // and now local throughput
          // chocoModel
          //   .arithm(busyTime, ">=", slotFinishTime(p)(s), "-", slotFinishTime(p)(s - 1))
          //   .post()
          // chocoModel
          //   .arithm(slotThroughput(p)(s), ">=", busyTime, "+", slotThroughput(p)(s - 1))
          //   .post()
          // chocoModel.arithm(slotThroughput(p)(s), ">=", slotFinishTime(p)(s), "-", slotStartTime(p)(s)).post()
          // now take care of communications
          for (a <- actors; pOther <- schedulers; if p != pOther) {
            val fetchTimes = channels.zipWithIndex
              .filter((_, c) => balanceMatrix(c)(a) < 0)
              .map((_, c) =>
                tileAsyncModule
                  .messageTravelDuration(c)(pOther)(p)
                  .mul(firingsInSlots(a)(pOther)(s))
                  .intVar()
              // schedulers.filter(pOther => pOther != p).map(pOther =>
              // )
              )
            val serialFetchTimes =
              chocoModel.sum(s"serialFetchTime($s, $p, $pOther)", fetchTimes: _*)
            chocoModel.ifThen(
              chocoModel.arithm(firingsInSlots(a)(p)(s), ">", 0),
              chocoModel.arithm(
                slotStartTime(p)(s),
                ">=",
                slotFinishTime(pOther)(s - 1),
                "+",
                serialFetchTimes
              )
            )
          }
        } else {
          chocoModel.arithm(slotStartTime(p)(0), "=", 0).post()
          // chocoModel.arithm(slotThroughput(p)(0), "=", duration(p)(s)).post()
        }
        chocoModel.ifThen(
          chocoModel.sum(actorFirings, ">", 0),
          chocoModel.arithm(
            invThroughputs(p),
            ">=",
            slotFinishTime(p)(slotRange.max),
            "-",
            slotStartTime(p)(s)
          )
        )
      }
    }
    // throughput
    chocoModel.max(globalInvThroughput, invThroughputs).post()

    // val thPropagator = SDFLikeThroughputPropagator(
    //   firingsInSlots,
    //   slotStartTime,
    //   slotFinishTime,
    //   invThroughputs,
    //   globalInvThroughput
    // )
    // chocoModel.post(
    //   new Constraint(
    //     "global_th_sdf_prop",
    //     thPropagator
    //   )
    // )
    // time
    // val timePropagator = SDFLikeTimingPropagator(
    //   actorDuration,
    //   balanceMatrix,
    //   initialTokens,
    //   firingsInSlots,
    //   channelsTravelTime,
    //   channelsCommunicate,
    //   slotStartTime,
    //   slotFinishTime
    // )
    // chocoModel.post(
    //   new Constraint(
    //     "global_time_sdf_prop",
    //     timePropagator
    //   )
    // )
    // tokens
  }

}

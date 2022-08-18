package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoCPForSyDeDecisionModel
import org.chocosolver.solver.Model
import forsyde.io.java.core.Vertex
import org.chocosolver.solver.Solution
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.choco.models.ManyProcessManyMessageMemoryConstraintsMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW
import idesyde.identification.forsyde.ForSyDeIdentificationRule
import idesyde.identification.choco.models.TileAsyncInterconnectCommsMixin
import spire.math.Rational
import idesyde.implicits.forsyde.given_Fractional_Rational
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.Largest
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMedian
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax

final case class ChocoSDFToSChedTileHW(
    val dse: SDFToSchedTiledHW
)(using Fractional[Rational])
    extends ChocoCPForSyDeDecisionModel
    with ManyProcessManyMessageMemoryConstraintsMixin
    with TileAsyncInterconnectCommsMixin
    with SDFTimingAnalysisSASMixin {

  val chocoModel: Model = Model()

  // section for time multiplier calculation
  val timeValues =
    (dse.wcets.flatten)
  var timeMultiplier = 1L
  while (
    timeValues
      .map(t => t * (timeMultiplier))
      .exists(d => d.doubleValue < 1) && timeMultiplier < Int.MaxValue / 4
  ) {
    timeMultiplier *= 10
  }
  // scribe.debug(timeMultiplier.toString)

  // do the same for memory numbers
  val memoryValues = dse.platform.tiledDigitalHardware.memories.map(_.getSpaceInBits().toLong) ++
    dse.sdfApplications.messagesMaxSizes ++
    dse.sdfApplications.processSizes
  var memoryDivider = 1L
  while (memoryValues.forall(_ / memoryDivider >= 100) && memoryDivider < Int.MaxValue) {
    memoryDivider *= 10L
  }

  val dataSize: Array[Int] =
    dse.sdfApplications.messagesMaxSizes.map(l => (l / memoryDivider).toInt)

  val processSize: Array[Int] =
    dse.sdfApplications.processSizes.map(l => (l / memoryDivider).toInt)

  val commElemsPaths = dse.platform.tiledDigitalHardware.computeRouterPaths
  def commElemsPath(srcIdx: Int)(dstIdx: Int): Array[Int] = commElemsPaths(srcIdx)(dstIdx)

  // TODO: figure out a way to make this not true, later
  def commElemsMustShareChannel(ceIdx: Int)(otherCeIdx: Int): Boolean = true

  def numVirtualChannels(ceIdx: Int): Int =
    dse.platform.tiledDigitalHardware.commElemsVirtualChannels(ceIdx)

  def messageTravelTimePerVirtualChannelById(messageIdx: Int)(ceId: Int): Int =
    (dse.sdfApplications.messagesMaxSizes(messageIdx) / dse.platform.tiledDigitalHardware
      .bandWidthPerCEPerVirtualChannelById(ceId) / timeMultiplier).ceil.toInt

  def numCommElems = dse.platform.tiledDigitalHardware.routerSet.size

  def numMessages = dse.sdfApplications.channelsSet.size

  def numProcElems = dse.platform.tiledDigitalHardware.tileSet.size

  def actors: Array[Int] = dse.sdfApplications.actorsSet

  def maxRepetitionsPerActors(actorId: Int): Int =
    dse.sdfApplications.sdfRepetitionVectors(actors.indexOf(actorId))

  def schedulers: Array[Int] = dse.platform.schedulerSet

  def isStaticCyclic(schedulerId: Int): Boolean =
    dse.platform.isStaticCycle(schedulers.indexOf(schedulerId))

  //-----------------------------------------------------
  // Decision variables
  val messagesMemoryMapping: Array[Array[BoolVar]] = dse.sdfApplications.channels.map(c =>
    dse.platform.tiledDigitalHardware.memories.map(mem =>
      chocoModel.boolVar(s"${c.getIdentifier()}_${mem.getIdentifier()}_m")
    )
  )

  val processesMemoryMapping: Array[Array[BoolVar]] = dse.sdfApplications.actors.map(a =>
    dse.platform.tiledDigitalHardware.memories.map(mem =>
      chocoModel.boolVar(s"${a.getIdentifier()}_${mem.getIdentifier()}_m")
    )
  )

  val messageAllocation = dse.sdfApplications.channels.map(c => {
    dse.platform.tiledDigitalHardware.tileSet.map(src => {
      dse.platform.tiledDigitalHardware.tileSet.map(dst => {
        if (!dse.platform.tiledDigitalHardware.computeRouterPaths(src)(dst).isEmpty)
          chocoModel.boolVar(s"send_${c.getIdentifier()}_${src}_${dst}")
        else
          chocoModel.boolVar(s"send_${c.getIdentifier()}_${src}_${dst}", false)
      })
    })
  })

  val messageTravelTimes = dse.sdfApplications.channels.zipWithIndex.map((c, i) => {
    dse.platform.tiledDigitalHardware.tileSet.map(src => {
      dse.platform.tiledDigitalHardware.tileSet.map(dst => {
        chocoModel.intVar(
          s"send_${c.getIdentifier()}_${src}_${dst}",
          0,
          commElemsPath(src)(dst)
            .map(ce =>
              // println(ce)
              messageTravelTimePerVirtualChannelById(i)(ce)
            )
            .sum,
          true
        )
      })
    })
  })

  val messageVirtualChannelAllocation = dse.sdfApplications.channels.zipWithIndex.map((c, i) => {
    dse.platform.tiledDigitalHardware.commElemsVirtualChannels.zipWithIndex.map((vcs, j) => {
      chocoModel.intVar(
        s"vc_${c.getIdentifier()}_${j}",
        1,
        vcs
      )
    })
  })

  val numActorsScheduledSlotsInStaticCyclicVars: Array[Array[Array[IntVar]]] =
    dse.sdfApplications.actors.zipWithIndex.map((a, i) => {
      dse.platform.schedulers.zipWithIndex.map((s, j) => {
        (0 until actors.size)
          .map(aa =>
            chocoModel
              .intVar(
                s"sched_${a.getIdentifier()}_${s.getIdentifier()}_${aa}",
                0,
                maxRepetitionsPerActors(actors.indexOf(i)),
                true
              )
          )
          .toArray
      })
    })

  //---------

  //-----------------------------------------------------
  // Objectives

  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      schedulers
        .map(p => actors.map(a => actorDuration(a)(p) * maxRepetitionsPerActors(a)).max)
        .max,
      schedulers
        .map(p => actors.map(a => actorDuration(a)(p) * maxRepetitionsPerActors(a)).sum)
        .max,
      true
    )

  val peIsUsed =
    dse.platform.tiledDigitalHardware.processors.zipWithIndex.map((pe, j) =>
      chocoModel.sum(processesMemoryMapping.map(t => t(j)), ">=", 1).reify
    )
  val nUsedPEs = chocoModel.intVar(
    "nUsedPEs",
    1,
    dse.platform.tiledDigitalHardware.processors.length
  )

  val memoryUsage: Array[IntVar] = dse.platform.tiledDigitalHardware.memories
    .map(m => (m, (m.getSpaceInBits() / memoryDivider).toInt))
    .map((m, s) => chocoModel.intVar(s"${m.getIdentifier()}_u", 0, s, true))

  def messageIsCommunicated(messageIdx: Int)(srcIdx: Int)(dstIdx: Int): BoolVar =
    messageAllocation(messageIdx)(srcIdx)(dstIdx)

  def messageTravelDuration(messageIdx: Int)(srcIdx: Int)(dstIdx: Int): IntVar =
    messageTravelTimes(messageIdx)(srcIdx)(dstIdx)

  def virtualChannelForMessage(messageIdx: Int)(ceIdx: Int): IntVar =
    messageVirtualChannelAllocation(messageIdx)(ceIdx)

  def numActorsScheduledSlotsInStaticCyclic(a: Int)(p: Int)(slot: Int) =
    numActorsScheduledSlotsInStaticCyclicVars(a)(p)(slot)

  // in a tile-based architecture, a process being mapped to a memory tile implies it is scheduled there too!
  def actorMapping(actorId: Int)(schedulerId: Int): BoolVar =
    processesMemoryMapping(actors.indexOf(actorId))(schedulers.indexOf(schedulerId))

  //-----------------------------------------------------
  // AUXILIARY VARIABLES

  val upperStartTimeBound = actors
    .map(a => schedulers.map(s => maxRepetitionsPerActors(a) * dse.wcets(a)(s)).sum)
    .max * timeMultiplier
  val startTimeOfActorFiringsVars = actors.map(a => {
    (0 until maxRepetitionsPerActors(a)).map(q =>
      chocoModel.intVar(s"time_fire_${a}", 0, upperStartTimeBound.ceil.toInt, true)
    )
  })
  def startTimeOfActorFirings(actorId: Int)(firing: Int): IntVar =
    startTimeOfActorFiringsVars(actorId)(firing)

  //---------

  //-----------------------------------------------------
  // MAPPING

  // - The channels can only be mapped in one tile, to avoid extra care on ordering and timing
  // - of the data
  // - therefore, every channel has to be mapped to exactly one tile
  dse.sdfApplications.channels.zipWithIndex.foreach((c, i) =>
    chocoModel.sum(messagesMemoryMapping(i), "=", 1).post()
  )

  // - every actor has to be mapped to at least one tile
  dse.sdfApplications.actors.zipWithIndex.foreach((a, i) =>
    chocoModel.sum(processesMemoryMapping(i), ">=", 1).post()
  )
  // - The number of parallel mappings should not exceed the repetition
  // vector when this can happen.
  // But is this necessary?
  // dse.sdfApplications.actors.zipWithIndex.foreach((a, i) =>
  //   if (dse.sdfApplications.sdfRepetitionVectors(i) <= dse.platform.schedulers.size)
  //     chocoModel.sum(processesMemoryMapping(i), "<=", dse.sdfApplications.sdfRepetitionVectors(i)).post()
  // )

  // - mixed constraints
  postManyProcessManyMessageMemoryConstraints()

  //---------

  //-----------------------------------------------------
  // COMMUNICATION

  // postTileAsyncInterconnectComms()
  //---------

  def actorDuration: Array[Array[Int]] =
    dse.wcets.map(as => as.map(w => w * timeMultiplier).map(_.ceil.toInt))
  def balanceMatrix: Array[Array[Int]] = dse.sdfApplications.balanceMatrices(0)
  def channelsTravelTime: Array[Array[Array[org.chocosolver.solver.variables.IntVar]]] =
    messageTravelTimes
  def firingsInSlots: Array[Array[Array[org.chocosolver.solver.variables.IntVar]]] =
    numActorsScheduledSlotsInStaticCyclicVars
  def initialLatencies: Array[org.chocosolver.solver.variables.IntVar] =
    dse.platform.schedulers.map(_ => chocoModel.intVar("lat", 1))
  def initialTokens: Array[Int] = dse.sdfApplications.initialTokens
  def slotMaxDuration(schedulerId: Int): org.chocosolver.solver.variables.IntVar =
    chocoModel.intVar("slot_dur", 1)
  def slotMaxDurations: Array[org.chocosolver.solver.variables.IntVar] =
    dse.platform.schedulers.map(_ => chocoModel.intVar("dur", 1))
  def slotPeriods: Array[org.chocosolver.solver.variables.IntVar] =
    dse.platform.schedulers.map(_ => chocoModel.intVar("per", 1))
  def startLatency(schedulerId: Int): org.chocosolver.solver.variables.IntVar =
    chocoModel.intVar("lat", 1)

  //-----------------------------------------------------
  // SCHEDULING AND TIMING

  // and sdf can be executed in a PE only if its mapped into this PE
  actors.foreach(a => {
    schedulers.foreach(p => {
      chocoModel.ifThen(
        processesMemoryMapping(a)(p).eq(1).decompose(),
        chocoModel.sum(s"firings_${a}_${p}>0", firingsInSlots(a)(p): _*).gt(0).decompose()
      )
      chocoModel.ifThen(
        processesMemoryMapping(a)(p).eq(0).decompose(),
        chocoModel.sum(s"firings_${a}_${p}=0", firingsInSlots(a)(p): _*).eq(0).decompose()
      )
    })
  })
  // postOnlySAS()
  postSDFTimingAnalysisSAS()
  //---------

  // make sure the variable counts the number of used
  chocoModel.sum(peIsUsed, "=", nUsedPEs).post

  override def modelObjectives: Array[IntVar] =
    Array(chocoModel.intMinusView(nUsedPEs), chocoModel.intMinusView(globalInvThroughput))
  //---------

  override def strategies: Array[AbstractStrategy[? <: Variable]] = Array(
    // Search.intVarSearch(
    //   Largest(),
    //   IntDomainMax(),
    //   firingsInSlots.flatten.flatten:_*
    // ),
    // Search.bestBound(
    SimpleMultiCoreSDFListScheduling(
      actors.zipWithIndex.map((a, i) => maxRepetitionsPerActors(i)),
      balanceMatrix,
      initialTokens,
      actorDuration,
      channelsTravelTime,
      firingsInSlots
      // )
    ),
    Search.defaultSearch(chocoModel)
    // Search.minDomLBSearch(globalInvThroughput),
    // Search.minDomLBSearch(nUsedPEs)
  )

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val channelToRouters = dse.sdfApplications.channels.zipWithIndex.map((c, i) => {
      dse.platform.tiledDigitalHardware.routerSet.map(j => {
        dse.platform.tiledDigitalHardware.tileSet.exists(src =>
          dse.platform.tiledDigitalHardware.tileSet.exists(dst =>
            commElemsPath(src)(dst).contains(j) && messageAllocation(i)(src)(dst).getValue() > 0
          )
        )
      })
    })
    // println(dse.platform.tiledDigitalHardware.routerPaths.map(_.map(_.mkString("[", ", ", "]")).mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    // println(channelToRouters.map(_.mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    val channelToTiles = dse.sdfApplications.channels.zipWithIndex.map((c, i) => {
      dse.platform.tiledDigitalHardware.tileSet.map(j => messagesMemoryMapping(i)(j).getValue() > 0)
    })
    val mappings = dse.sdfApplications.channels.zipWithIndex
      .map((c, i) => channelToTiles(i) ++ channelToRouters(i))
    val schedulings =
      processesMemoryMapping.map(vs => vs.map(v => if (v.getValue() > 0) then true else false))
    println(
      schedulers
        .map(s => {
          (0 until actors.size)
            .map(slot => {
              actors.zipWithIndex
                .find((a, ai) => firingsInSlots(ai)(s)(slot).getLB() > 0)
                .map((a, ai) => a.toString())
                .getOrElse("_")
            })
            .mkString("[", ", ", "]")
        })
        .mkString("[\n ", "\n ", "\n]")
    )
    dse.addMappingsAndRebuild(
      mappings,
      schedulings,
      numActorsScheduledSlotsInStaticCyclicVars.map(_.map(_.map(_.getValue())))
    )
  }

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW"

  def coveredVertexes: Iterable[Vertex] = dse.coveredVertexes

}

object ChocoSDFToSChedTileHW extends ForSyDeIdentificationRule[ChocoSDFToSChedTileHW] {
  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ): IdentificationResult[ChocoSDFToSChedTileHW] = {
    identified
      .find(m => m.isInstanceOf[SDFToSchedTiledHW])
      .map(m => m.asInstanceOf[SDFToSchedTiledHW])
      .map(dse => identFromForSyDeWithDeps(model, dse))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      dse: SDFToSchedTiledHW
  ): IdentificationResult[ChocoSDFToSChedTileHW] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHW(dse))
  }

}

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
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsMixin
import org.chocosolver.solver.search.strategy.strategy.FindAndProve

final case class ChocoSDFToSChedTileHWSlowest(
    val dse: SDFToSchedTiledHW
)(using Fractional[Rational])
    extends ChocoCPForSyDeDecisionModel
    with SingleProcessSingleMessageMemoryConstraintsMixin
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

  // ---- SDFTimingAnalysisSASMixin
  val sdfAndSchedulers = dse

  val invThroughputs: Array[IntVar] = schedulers
    .map(p =>
      chocoModel.intVar(
        s"invTh($p)",
        0,
        actors.zipWithIndex.map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai)).sum,
        true
      )
    )
  val slotFinishTime: Array[Array[IntVar]] = schedulers
    .map(p =>
      actors.map(s => chocoModel.intVar(s"finishTime($p, $s)", 0, invThroughputs(p).getUB(), true))
    )
  val slotStartTime: Array[Array[IntVar]] = schedulers
    .map(p =>
      actors.map(s => chocoModel.intVar(s"startTime($p, $s)", 0, invThroughputs(p).getUB(), true))
    )
  val globalInvThroughput =
    chocoModel.intVar(
      "globalInvThroughput",
      schedulers
        .map(p => actors.map(a => actorDuration(a)(p)).max)
        .max,
      schedulers
        .map(p =>
          actors.zipWithIndex.map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai)).sum
        )
        .max,
      true
    )

  override val timeFactor = timeMultiplier

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
  def commElemsPath(srcId: Int)(dstId: Int): Array[Int] =
    commElemsPaths(procElems.indexOf(srcId))(procElems.indexOf(dstId))

  // TODO: figure out a way to make this not true, later
  def commElemsMustShareChannel(ceId: Int)(otherCeId: Int): Boolean = true

  def numVirtualChannels(ceId: Int): Int =
    dse.platform.tiledDigitalHardware.commElemsVirtualChannels(commElems.indexOf(ceId))

  def messageTravelTimePerVirtualChannelById(messageId: Int)(ceId: Int): Int =
    dataSize(messages.indexOf(messageId)) / (dse.platform.tiledDigitalHardware
      .bandWidthPerCEPerVirtualChannelById(ceId)
      * timeMultiplier * memoryDivider).ceil.toInt

  def commElems = dse.platform.tiledDigitalHardware.routerSet

  def messages = dse.sdfApplications.channelsSet

  def procElems = dse.platform.tiledDigitalHardware.tileSet

  def isStaticCyclic(schedulerId: Int): Boolean =
    dse.platform.isStaticCycle(schedulers.indexOf(schedulerId))

  //-----------------------------------------------------
  // Decision variables
  val messagesMemoryMapping: Array[IntVar] = dse.sdfApplications.channels.map(c =>
    chocoModel.intVar(s"${c.getIdentifier()}_m", dse.platform.tiledDigitalHardware.tileSet)
  )

  val processesMemoryMapping: Array[IntVar] = dse.sdfApplications.actors.map(a =>
    chocoModel.intVar(s"${a.getIdentifier()}_m", dse.platform.tiledDigitalHardware.tileSet)
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

  val messageTravelTimes = dse.sdfApplications.channelsSet.zipWithIndex.map((c, i) => {
    dse.platform.tiledDigitalHardware.tileSet.map(src => {
      dse.platform.tiledDigitalHardware.tileSet.map(dst => {
        chocoModel.intVar(
          s"time_${c}_${src}_${dst}",
          commElemsPath(src)(dst)
            .map(ce =>
              // println(ce)
              messageTravelTimePerVirtualChannelById(c)(ce)
            )
            .minOption
            .getOrElse(0),
          commElemsPath(src)(dst)
            .map(ce =>
              // println(ce)
              messageTravelTimePerVirtualChannelById(c)(ce)
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
        0,
        vcs
      )
    })
  })

  val numActorsScheduledSlotsInStaticCyclicVars: Array[Array[Array[IntVar]]] =
    dse.sdfApplications.actors.zipWithIndex.map((a, i) => {
      dse.platform.executionSchedulers.zipWithIndex.map((s, j) => {
        (0 until actors.size)
          .map(aa =>
            chocoModel
              .intVar(
                s"sched_${a.getIdentifier()}_${s.getIdentifier()}_${aa}",
                0,
                maxRepetitionsPerActors(i),
                true
              )
          )
          .toArray
      })
    })

  //---------

  //-----------------------------------------------------
  // Objectives

  // remember that this is tiled based, so a memory being mapped entails the processor
  // is used
  // val peIsUsed =
  //   dse.platform.tiledDigitalHardware.tileSet.map(pe =>
  //     chocoModel.atLeastNValues(s"${pe}_used", pe, processesMemoryMapping:_*)
  //     // chocoModel.sum(processesMemoryMapping.map(t => t(j)), ">=", 1).reify
  //   )
  val nUsedPEs = chocoModel.intVar(
    "nUsedPEs",
    1,
    dse.platform.tiledDigitalHardware.processors.length
  )
  chocoModel.atMostNValues(processesMemoryMapping, nUsedPEs, true).post()

  val memoryUsage: Array[IntVar] = dse.platform.tiledDigitalHardware.memories
    .map(m => (m, (m.getSpaceInBits() / memoryDivider).toInt))
    .map((m, s) => chocoModel.intVar(s"${m.getIdentifier()}_u", 0, s, true))

  def messageIsCommunicated(messageId: Int)(srcId: Int)(dstId: Int): BoolVar =
    messageAllocation(messages.indexOf(messageId))(procElems.indexOf(srcId))(
      procElems.indexOf(dstId)
    )

  def messageTravelDuration(messageId: Int)(srcId: Int)(dstId: Int): IntVar =
    messageTravelTimes(messages.indexOf(messageId))(procElems.indexOf(srcId))(
      procElems.indexOf(dstId)
    )

  def virtualChannelForMessage(messageId: Int)(ceId: Int): IntVar =
    messageVirtualChannelAllocation(messages.indexOf(messageId))(commElems.indexOf(ceId))

  def numActorsScheduledSlotsInStaticCyclic(a: Int)(p: Int)(slot: Int) =
    numActorsScheduledSlotsInStaticCyclicVars(a)(p)(slot)

  // in a tile-based architecture, a process being mapped to a memory tile implies it is scheduled there too!
  // def actorMapping(actorId: Int): IntVar =
  //   processesMemoryMapping(actors.indexOf(actorId))

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

  def channelsCommunicate = messageAllocation

  //---------

  //-----------------------------------------------------
  // MAPPING

  // - The channels can only be mapped in one tile, to avoid extra care on ordering and timing
  // - of the data
  // - therefore, every channel has to be mapped to exactly one tile
  // dse.sdfApplications.channels.zipWithIndex.foreach((c, i) =>
  //   chocoModel.sum(messagesMemoryMapping(i), "=", 1).post()
  // )

  // - every actor has to be mapped to at least one tile
  // dse.sdfApplications.actors.zipWithIndex.foreach((a, i) =>
  //   chocoModel.sum(processesMemoryMapping(i), ">=", 1).post()
  // )
  // - The number of parallel mappings should not exceed the repetition
  // vector when this can happen.
  // But is this necessary?
  // dse.sdfApplications.actors.zipWithIndex.foreach((a, i) =>
  //   if (dse.sdfApplications.sdfRepetitionVectors(i) <= dse.platform.schedulers.size)
  //     chocoModel.sum(processesMemoryMapping(i), "<=", dse.sdfApplications.sdfRepetitionVectors(i)).post()
  // )

  // - mixed constraints
  postSingleProcessSingleMessageMemoryConstraints()

  //---------

  //-----------------------------------------------------
  // COMMUNICATION

  processesMemoryMapping.zipWithIndex.foreach((srca, srci) => {
    processesMemoryMapping.zipWithIndex.foreach((dsta, dsti) => {
      if (srca != dsta) {
        messages.zipWithIndex.foreach((c, ci) => {
          if (balanceMatrix(ci)(srci) > 0 && balanceMatrix(ci)(dsti) < 0) {
            schedulers.zipWithIndex.foreach((_, sendi) => {
              schedulers.zipWithIndex.foreach((_, recvi) => {
                if (sendi != recvi) {
                  chocoModel.ifThen(
                    srca.eq(sendi).and(dsta.eq(recvi)).decompose(),
                    messageAllocation(ci)(sendi)(recvi).eq(1).decompose()
                  )
                }
              })
            })
          }
        })
      }
    })
  })

  for (srca <- actors; dsta <- actors; if srca != dsta; c <- messages) {
    // chocoModel.ifThen(processesMemoryMapping(srca))
  }

  postTileAsyncInterconnectComms()
  //---------

  val channelsTravelTime: Array[Array[Array[org.chocosolver.solver.variables.IntVar]]] =
    messageTravelTimes
  val firingsInSlots: Array[Array[Array[org.chocosolver.solver.variables.IntVar]]] =
    numActorsScheduledSlotsInStaticCyclicVars
  val initialLatencies: Array[org.chocosolver.solver.variables.IntVar] =
    dse.platform.executionSchedulers.map(_ => chocoModel.intVar("lat", 1))
  def slotMaxDuration(schedulerId: Int): org.chocosolver.solver.variables.IntVar =
    chocoModel.intVar("slot_dur", 1)
  val slotMaxDurations: Array[org.chocosolver.solver.variables.IntVar] =
    dse.platform.executionSchedulers.map(_ => chocoModel.intVar("dur", 1))
  val slotPeriods: Array[org.chocosolver.solver.variables.IntVar] =
    dse.platform.executionSchedulers.map(_ => chocoModel.intVar("per", 1))
  def startLatency(schedulerId: Int): org.chocosolver.solver.variables.IntVar =
    chocoModel.intVar("lat", 1)

  //-----------------------------------------------------
  // SCHEDULING AND TIMING

  // and sdf can be executed in a PE only if its mapped into this PE
  actors.foreach(a => {
    schedulers.foreach(p => {
      chocoModel.ifOnlyIf(
        processesMemoryMapping(a).eq(p).decompose(),
        chocoModel.sum(s"firings_${a}_${p}>0", firingsInSlots(a)(p): _*).gt(0).decompose()
      )
      chocoModel.ifThen(
        processesMemoryMapping(a).ne(p).decompose(),
        chocoModel.sum(s"firings_${a}_${p}=0", firingsInSlots(a)(p): _*).eq(0).decompose()
      )
    })
  })
  // postOnlySAS()
  postSDFTimingAnalysisSAS()
  //---------

  // make sure the variable counts the number of used
  // chocoModel.sum(peIsUsed, "=", nUsedPEs).post

  override def modelObjectives: Array[IntVar] =
    Array(chocoModel.intMinusView(nUsedPEs), chocoModel.intMinusView(globalInvThroughput))
  //---------

  //-----------------------------------------------------
  // BRANCHING AND SEARCH

  val listScheduling = SimpleMultiCoreSDFListScheduling(
    actors.zipWithIndex.map((a, i) => maxRepetitionsPerActors(i)),
    balanceMatrix,
    initialTokens,
    actorDuration,
    channelsTravelTime,
    firingsInSlots
  )

  override def strategies: Array[AbstractStrategy[? <: Variable]] = Array(
    // Search.bestBound(

    // Search.minDomLBSearch(globalInvThroughput),
    // Search.intVarSearch(
    //   Largest(),
    //   IntDomainMax(),
    //   firingsInSlots.flatten.flatten:_*
    // ),
    // FindAndProve((nUsedPEs +: firingsInSlots.flatten.flatten),
    listScheduling,
    Search.minDomLBSearch(nUsedPEs),
    // ),
    Search.minDomLBSearch(globalInvThroughput),
    // Search.minDomLBSearch(invThroughputs:_*),
    Search.minDomLBSearch(channelsCommunicate.flatten.flatten: _*),
    Search.minDomLBSearch(messageVirtualChannelAllocation.flatten: _*),
    Search.minDomLBSearch(slotStartTime.flatten: _*),
    Search.minDomLBSearch(slotFinishTime.flatten: _*),
    // Search.intVarSearch()
    Search.defaultSearch(chocoModel)
  )

  //---------

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val channelToRouters = dse.sdfApplications.channels.zipWithIndex.map((c, i) => {
      dse.platform.tiledDigitalHardware.routerSet.map(j => {
        dse.platform.tiledDigitalHardware.tileSet.exists(src =>
          dse.platform.tiledDigitalHardware.tileSet.exists(dst =>
            commElemsPath(src)(dst).contains(j) && output.getIntVal(
              messageAllocation(i)(src)(dst)
            ) > 0
          )
        )
      })
    })
    // println(dse.platform.tiledDigitalHardware.routerPaths.map(_.map(_.mkString("[", ", ", "]")).mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    // println(channelToRouters.map(_.mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    val channelToTiles = dse.sdfApplications.channels.zipWithIndex.map((c, i) => {
      dse.platform.tiledDigitalHardware.tileSet.map(j =>
        output.getIntVal(messagesMemoryMapping(i)) == j
      )
    })
    val mappings = dse.sdfApplications.channels.zipWithIndex
      .map((c, i) => channelToTiles(i) ++ channelToRouters(i))
    val schedulings =
      processesMemoryMapping.map(vs =>
        dse.platform.tiledDigitalHardware.tileSet.map(j => output.getIntVal(vs) == j)
      )
    println(
      schedulers
        .map(s => {
          (0 until actors.size)
            .map(slot => {
              actors.zipWithIndex
                .find((a, ai) => firingsInSlots(ai)(s)(slot).getLB() > 0)
                .map((a, ai) =>
                  dse.sdfApplications.actors(a).getIdentifier() + ": " + output
                    .getIntVal(firingsInSlots(ai)(s)(slot))
                )
                .getOrElse("_")
            })
            .mkString("[", ", ", "]")
        })
        .mkString("[\n ", "\n ", "\n]")
    )
    println(
      schedulers
        .map(src => {
          schedulers
            .map(dst => {
              messages.zipWithIndex
                .filter((c, ci) => messageAllocation(ci)(src)(dst).getLB() > 0)
                .map((c, ci) =>
                  c + ": " + commElemsPaths(src)(dst).zipWithIndex
                    .map((ce, cei) => ce + "/" + output.getIntVal(virtualChannelForMessage(c)(ce)))
                    .mkString("-")
                )
                .mkString("(", ", ", ")")
            })
            .mkString("[", ", ", "]")
        })
        .mkString("[\n ", "\n ", "\n]")
    )
    dse.addMappingsAndRebuild(
      mappings,
      schedulings,
      numActorsScheduledSlotsInStaticCyclicVars.map(_.map(_.map(output.getIntVal(_)))),
      messageVirtualChannelAllocation.map(_.map(output.getIntVal(_)))
    )
  }

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHWSlowest"

  def coveredVertexes: Iterable[Vertex] = dse.coveredVertexes

}

object ChocoSDFToSChedTileHWSlowest
    extends ForSyDeIdentificationRule[ChocoSDFToSChedTileHWSlowest] {
  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ): IdentificationResult[ChocoSDFToSChedTileHWSlowest] = {
    identified
      .find(m => m.isInstanceOf[SDFToSchedTiledHW])
      .map(m => m.asInstanceOf[SDFToSchedTiledHW])
      .map(dse => identFromForSyDeWithDeps(model, dse))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      dse: SDFToSchedTiledHW
  ): IdentificationResult[ChocoSDFToSChedTileHWSlowest] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHWSlowest(dse))
  }

}

package idesyde.identification.models.reactor

import idesyde.identification.interfaces.MiniZincDecisionModel
import scala.io.Source
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.MiniZincData
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.apache.commons.math3.util.ArithmeticUtils
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import org.apache.commons.math3.fraction.BigFraction

final case class ReactorMinusJobsMapAndSchedMzn(val sourceModel: ReactorMinusJobsMapAndSched)
    extends MiniZincDecisionModel:

  val coveredVertexes = sourceModel.coveredVertexes

  // TODO: fix here, not found in jar
  def mznModel =
    // val input     = getClass.getResourceAsStream("/minizinc/reactorminus_jobs_to_networkedHW.mzn")
    // val src       = Source.fromResource(input)
    // val mznString = src.getLines.mkString
    // src.close
    Source.fromResource("minizinc/reactorminus_jobs_to_networkedHW.mzn").mkString

  def mznInputs =
    val multiplier = sourceModel.reactorMinusJobs.jobs
      .map(_.trigger)
      .map(_.getDenominatorAsLong)
      .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))
    val hyperPeriod = sourceModel.reactorMinusJobs.reactorMinusApp.hyperPeriod
    val multHyperPeriod = hyperPeriod.multiply(multiplier).longValue
    // val reactorToIdx = sourceModel.reactorMinusJobs.reactorMinusApp.reactors.zipWithIndex.toMap
    val reactionToJobs = sourceModel.reactorMinusJobs.jobs.groupBy(_.srcReaction)
    val reactorsIdx = sourceModel.reactorMinusJobs.reactorMinusApp.reactors.toIndexedSeq
    val reactionsIdx   = sourceModel.reactorMinusJobs.reactorMinusApp.reactions.toIndexedSeq
    val channelsIdx    = sourceModel.reactorMinusJobs.reactorMinusApp.channels.toIndexedSeq
    val jobsIdx        = sourceModel.reactorMinusJobs.jobs.toIndexedSeq
    val jobChannelsIdx = sourceModel.reactorMinusJobs.channels.toIndexedSeq
    val platformIdx    = sourceModel.platform.hardware.platformElements.toIndexedSeq
    val processingElemsIdx = platformIdx.zipWithIndex.filter((p, i) => p.isInstanceOf[GenericProcessingModule])
      .map((p, i) => (p.asInstanceOf[GenericProcessingModule], i))
    val commElemsIdx = platformIdx.zipWithIndex.filter((p, i) => p.isInstanceOf[GenericDigitalInterconnect])
      .map((p, i) => (p.asInstanceOf[GenericDigitalInterconnect], i))
    val storageElemsIdx = platformIdx.zipWithIndex
          .filter((p, i) => p.isInstanceOf[GenericDigitalStorage])
          .map((p, i) => (p.asInstanceOf[GenericDigitalStorage], i))
    val jobChainsIdx = sourceModel.reactorMinusJobs.unambigousJobTriggerChains.toIndexedSeq
    Map(
      "hyperPeriod" -> MiniZincData(multHyperPeriod),
      "Reactors" -> MiniZincData(
        reactorsIdx.indices.toSet
      ),
      "Reactions" -> MiniZincData(reactionsIdx.indices.toSet),
      "Channels"  -> MiniZincData(channelsIdx.indices.toSet),
      "Jobs"      -> MiniZincData(jobsIdx.indices.toSet),
      "JobPureChannels" -> MiniZincData(
        jobChannelsIdx.zipWithIndex
          .filter((c, i) => sourceModel.reactorMinusJobs.pureChannels.contains(c))
          .map(_._2)
          .toSet
      ),
      "JobInnerStateChannels" -> MiniZincData(
        jobChannelsIdx.zipWithIndex
          .filter((c, i) => sourceModel.reactorMinusJobs.stateChannels.contains(c))
          .map(_._2)
          .toSet
      ),
      "PlatformElems" -> MiniZincData(
        platformIdx.indices.sorted.toSet
      ),
      "TimeTriggeredElems" -> MiniZincData(
        processingElemsIdx
          .filter((p, i) => sourceModel.platform.timeTriggeredPEs.contains(p))
          .map(_._2)
          .toSet
      ),
      "RoundRobinElems" -> MiniZincData(
        processingElemsIdx
          .filter((p, i) => sourceModel.platform.roundRobinPEs.contains(p))
          .map(_._2)
          .toSet
      ),
      "ProcessingElems" -> MiniZincData(
        processingElemsIdx
          .filter((p, i) => sourceModel.platform.hardware.processingElems.contains(p))
          .map(_._2)
          .toSet
      ),
      "CommunicationElems" -> MiniZincData(
        commElemsIdx
          .filter((p, i) => sourceModel.platform.hardware.communicationElems.contains(p))
          .map(_._2)
          .toSet
      ),
      "MemoryElems" -> MiniZincData(
        storageElemsIdx
          .filter((p, i) => sourceModel.platform.hardware.storageElems.contains(p))
          .map(_._2)
          .toSet
      ),
      "JobChains" -> MiniZincData(
        jobChainsIdx.indices.toSet
      ),
      "reactorSize" -> MiniZincData(
        reactorsIdx.map(a => a.getStateSizesInBits.stream.mapToLong(l => l).sum)
      ),
      "containingReactor" -> MiniZincData(
        reactionsIdx.map(r => reactorsIdx.indexOf(sourceModel.reactorMinusJobs.reactorMinusApp.containmentFunction(r)))
      ),
      "channelSize" -> MiniZincData(
        channelsIdx.map((rr, c) => c.getSizeInBits)
      ),
      "channelSrc" -> MiniZincData(
        channelsIdx.map((rr, c) => reactionsIdx.indexOf(rr._1))
      ),
      "channelDst" -> MiniZincData(
        channelsIdx.map((rr, c) => reactionsIdx.indexOf(rr._2))
      ),
      "originalReaction" -> MiniZincData(
        jobsIdx.map(j => reactionsIdx.indexOf(j.srcReaction))
      ),
      "jobRelease" -> MiniZincData(
        jobsIdx.map(j => j.trigger.multiply(multiplier).longValue)
      ),
      "jobDeadline" -> MiniZincData(
        jobsIdx.map(j => j.deadline.multiply(multiplier).longValue)
      ),
      "jobDataSize" -> MiniZincData(
        jobsIdx.map(j => {
          // TODO: fix this properly
          0
        })
      ),
      "jobWcet" -> MiniZincData(
        jobsIdx.map(j => {
          processingElemsIdx.map((p, i) => {
            sourceModel.wcetFunction.getOrElse((j, p), BigFraction.ZERO).multiply(multiplier).doubleValue.ceil.toLong
          })
        })
      ),
      "jobUtilization" -> MiniZincData(
        jobsIdx.map(j => {
          processingElemsIdx.map((p, i) => {
            sourceModel.wcetFunction.getOrElse((j, p), BigFraction.ZERO).divide(hyperPeriod).percentageValue.ceil.toLong
          })
        })
      ),
      "jobCanBeExecuted" -> MiniZincData(
        jobsIdx.map(j => {
          processingElemsIdx.map((p, i) => {
            sourceModel.wcetFunction.contains((j, p))
          })
        })
      ),
      "jobChannelSrc" -> MiniZincData(
        jobChannelsIdx.map(c => jobsIdx.indexOf(c.src))
      ),
      "jobChannelDst" -> MiniZincData(
        jobChannelsIdx.map(c => jobsIdx.indexOf(c.dst))
      )
    )

  def rebuildFromMznOutputs(
      output: Map[String, MiniZincData],
      originalModel: ForSyDeModel
  ): ForSyDeModel =
    ForSyDeModel()

  override def dominates(other: DecisionModel): Boolean =
    super.dominates(other) && (other match {
      case mzn: ReactorMinusJobsMapAndSched => sourceModel == mzn
      case _                                => true
    })

end ReactorMinusJobsMapAndSchedMzn

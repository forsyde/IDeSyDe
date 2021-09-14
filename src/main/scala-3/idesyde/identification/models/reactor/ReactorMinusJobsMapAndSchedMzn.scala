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
import forsyde.io.java.typed.viewers.RoundRobinScheduler
import forsyde.io.java.typed.viewers.GenericMemoryModule

final case class ReactorMinusJobsMapAndSchedMzn(val sourceModel: ReactorMinusJobsMapAndSched)
    extends MiniZincDecisionModel:

  val coveredVertexes = sourceModel.coveredVertexes

  lazy val multiplier = sourceModel.reactorMinusJobs.jobs
    .map(_.trigger)
    .map(_.getDenominatorAsLong)
    .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))
  lazy val hyperPeriod     = sourceModel.reactorMinusJobs.reactorMinusApp.hyperPeriod
  lazy val multHyperPeriod = hyperPeriod.multiply(multiplier).longValue
  lazy val reactionToJobs = sourceModel.reactorMinusJobs.jobs.groupBy(_.srcReaction)
  lazy val reactorsOrdered    = sourceModel.reactorMinusJobs.reactorMinusApp.reactors.toSeq
  lazy val reactionsOrdered   = sourceModel.reactorMinusJobs.reactorMinusApp.reactions.toSeq
  lazy val channelsOrdered    = sourceModel.reactorMinusJobs.reactorMinusApp.channels.toSeq
  lazy val jobsOrdered        = sourceModel.reactorMinusJobs.jobs.toSeq
  lazy val jobChannelsOrdered = sourceModel.reactorMinusJobs.channels
    .diff(sourceModel.reactorMinusJobs.outerStateChannels)
    .toSeq
  lazy val platformOrdered    = sourceModel.platform.hardware.platformElements.toSeq
  lazy val jobChainsOrdered = sourceModel.reactorMinusJobs.unambigousJobTriggerChains.toSeq

  lazy val mznModel = Source.fromResource("minizinc/reactorminus_jobs_to_networkedHW.mzn").mkString

  lazy val mznInputs =
    Map(
      "hyperPeriod" -> MiniZincData(multHyperPeriod),
      "nReactors" -> MiniZincData(reactorsOrdered.length),
      "nReactions" -> MiniZincData(reactionsOrdered.length),
      "nChannels"  -> MiniZincData(channelsOrdered.length),
      "nJobs"      -> MiniZincData(jobsOrdered.length),
      "nJobChannels" -> MiniZincData(jobChannelsOrdered.length),
      "nPlatformElems" -> MiniZincData(platformOrdered.length),
      "nJobChains" -> MiniZincData(jobChainsOrdered.length),
      "isTimeTriggeredElem" -> MiniZincData(
        platformOrdered.map(p => {
          p match
            case pp: GenericProcessingModule =>
              sourceModel.platform.timeTriggeredPEs.contains(pp)
            case _ => 
              false
          // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.timeTriggeredPEs.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isRoundRobinElem" -> MiniZincData(
        platformOrdered.map(p => {
          p match
            case pp: GenericProcessingModule =>
              sourceModel.platform.roundRobinPEs.contains(pp)
            case _ => 
              false
          // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.roundRobinPEs.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isProcessingElem" -> MiniZincData(
        platformOrdered.map(p => {
          p match
            case pp: GenericProcessingModule =>
              sourceModel.platform.hardware.processingElems.contains(pp)
            case _ => false
          // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.hardware.processingElems.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isCommunicationElem" -> MiniZincData(
        platformOrdered.map(p => {
          p match
            case pp: GenericDigitalInterconnect =>
              sourceModel.platform.hardware.communicationElems.contains(pp)
            case _ => false
          // p.isInstanceOf[GenericDigitalInterconnect] && sourceModel.platform.hardware.communicationElems.contains(p.asInstanceOf[GenericDigitalInterconnect]))
      })),
      "isMemoryElem" -> MiniZincData(
        platformOrdered.map(p => {
          p match
            case pp: GenericMemoryModule =>
              sourceModel.platform.hardware.storageElems.contains(pp)
            case _ => false
          // p.isInstanceOf[GenericMemoryModule] && sourceModel.platform.hardware.storageElems.contains(p.asInstanceOf[GenericMemoryModule]))
      })),
      "reactorSize" -> MiniZincData(
        reactorsOrdered.map(a => a.getStateSizesInBits.stream.mapToLong(l => l).sum)
      ),
      "containingReactor" -> MiniZincData(
        reactionsOrdered.map(r =>
          reactorsOrdered.indexOf(sourceModel.reactorMinusJobs.reactorMinusApp.containmentFunction(r)) + 1
        )
      ),
      "channelSize" -> MiniZincData(
        channelsOrdered.map((rr, c) => c.getSizeInBits)
      ),
      "channelSrc" -> MiniZincData(
        channelsOrdered.map((rr, c) => reactionsOrdered.indexOf(rr._1) + 1)
      ),
      "channelDst" -> MiniZincData(
        channelsOrdered.map((rr, c) => reactionsOrdered.indexOf(rr._2) + 1)
      ),
      "originalReaction" -> MiniZincData(
        jobsOrdered.map(j => reactionsOrdered.indexOf(j.srcReaction) + 1)
      ),
      "jobRelease" -> MiniZincData(
        jobsOrdered.map(j => j.trigger.multiply(multiplier).longValue)
      ),
      "jobDeadline" -> MiniZincData(
        jobsOrdered.map(j => j.deadline.multiply(multiplier).longValue)
      ),
      "jobDataSize" -> MiniZincData(
        jobsOrdered.map(j => {
          // TODO: fix this properly
          0
        })
      ),
      "jobWcet" -> MiniZincData(
        jobsOrdered.map(j => {
          platformOrdered
          .map(p => {
            p match
              case pe: GenericProcessingModule =>
                sourceModel.wcetFunction
                  .getOrElse((j, pe), BigFraction.ZERO)
                  .multiply(multiplier)
                  .doubleValue
                  .ceil
                  .toLong
              case _ => 0
          })
        })
      ),
      "jobUtilization" -> MiniZincData(
        jobsOrdered.map(j => {
          platformOrdered
          .map(p => {
            p match
              case pe: GenericProcessingModule =>
                sourceModel.wcetFunction
                  .getOrElse((j, pe), BigFraction.ZERO)
                  .divide(hyperPeriod)
                  .percentageValue
                  .ceil
                  .toLong
              case _ => 0
          })
        })
      ),
      "jobCanBeExecuted" -> MiniZincData(
        jobsOrdered.map(j => {
          platformOrdered
          .map(p => {
            p match
              case pe: GenericProcessingModule =>
                sourceModel.wcetFunction.contains((j, pe))
              case _ => false
          })
        })
      ),
      "jobChannelSrc" -> MiniZincData(
        jobChannelsOrdered.map(c => jobsOrdered.indexOf(c.src) + 1)
      ),
      "jobChannelDst" -> MiniZincData(
        jobChannelsOrdered.map(c => jobsOrdered.indexOf(c.dst) + 1)
      ),
      "roundRobinElemsMinSlice" -> MiniZincData(
        platformOrdered
          .map(p => {
            p match
              case pe: GenericProcessingModule =>
                if (sourceModel.platform.roundRobinPEs.contains(pe))
                  sourceModel.platform.schedulersFromPEs(pe) match
                    case s: RoundRobinScheduler =>
                      s.getMinimumTimeSliceInCycles
                    case _ => 0
                else 0
              case _ => 0
          })
      ),
      "roundRobinElemsMaxSlice" -> MiniZincData(
        platformOrdered
          .map(p => {
            p match
              case pe: GenericProcessingModule =>
                if (sourceModel.platform.roundRobinPEs.contains(pe))
                  sourceModel.platform.schedulersFromPEs(pe) match
                    case s: RoundRobinScheduler =>
                      s.getMaximumTimeSliceInCycles
                    case _ => 0
                else 0
              case _ => 0
          })
      ),
      "platformPaths" -> MiniZincData(
        platformOrdered.map(src => {
          platformOrdered.map(dst => {
            val path = sourceModel.platform.hardware.paths.getOrElse((src, dst), Seq.empty)
            platformOrdered.map(p => if (path.contains(p)) path.indexOf(p) + 1 else 0)
          })
        })
      ),
      "allocatedBandwidth" -> MiniZincData(
        platformOrdered
        .map(com => {
          platformOrdered
          .map(pe => {
            com match
              case c: GenericDigitalInterconnect =>
                pe match
                  case p: GenericProcessingModule =>
                    Math.floorDiv(
                      sourceModel.platform.hardware.bandWidthBitPerSec.getOrElse((c, p), 0.toLong),
                      multiplier
                    )
                  case _ => 0
              case _ => 0
          })
        })
      ),
      "memoryMaximumCapacity" -> MiniZincData(
        platformOrdered
        .map(p => {
          p match
            case m: GenericMemoryModule =>
              m.getMaxMemoryInBits
            case _ => 0
        })
      ),
      "firstInChain" -> MiniZincData(
        jobChainsOrdered.map(js => jobsOrdered.indexOf(js.head) + 1)
      ),
      "lastInChain" -> MiniZincData(
        jobChainsOrdered.map(js => jobsOrdered.indexOf(js.last) + 1)
      ),
      "objLambda" -> MiniZincData(0)
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

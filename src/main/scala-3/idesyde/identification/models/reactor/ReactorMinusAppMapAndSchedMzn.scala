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

final case class ReactorMinusAppMapAndSchedMzn(val sourceModel: ReactorMinusAppMapAndSched)
    extends MiniZincDecisionModel:

  val coveredVertexes = sourceModel.coveredVertexes

  var multiplier =
    sourceModel.reactorMinus.jobGraph.jobs
      .map(_.trigger)
      .map(_.getDenominatorAsLong)
      .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))
  while (
    sourceModel.wcetFunction.values
      .map(_.multiply(multiplier))
      .forall(v => v.getNumeratorAsLong / 1e3 < v.getDenominatorAsLong)
  ) {
    multiplier = multiplier * 10
  }
  val hyperPeriod                = sourceModel.reactorMinus.hyperPeriod
  val reactionToJobs             = sourceModel.reactorMinus.jobGraph.jobs.groupBy(_.srcReaction)
  val reactorsOrdered            = sourceModel.reactorMinus.reactors.toSeq
  val reactionsOrdered           = sourceModel.reactorMinus.reactions.toSeq
  val channelsOrdered            = sourceModel.reactorMinus.channels.toSeq
  lazy val jobsOrdered           = sourceModel.reactorMinus.jobGraph.jobs.toSeq
  lazy val jobChannelsOrdered    = sourceModel.reactorMinus.jobGraph.inChannels.toSeq
  val platformOrdered            = sourceModel.platform.hardware.platformElements.toSeq
  val reactionChainsOrdered      = sourceModel.reactorMinus.unambigousEndToEndReactions.toSeq
  lazy val jobChainsOrdered      = sourceModel.reactorMinus.jobGraph.unambigousEndToEndJobs
  lazy val symmetryGroupsOrdered = sourceModel.computationallySymmetricGroups.toSeq

  val mznModel = Source.fromResource("minizinc/reactorminus_to_networkedHW.mzn").mkString
  // scribe.debug(platformOrdered.toString)
  // scribe.debug(reactionsOrdered.toString)
  // scribe.debug(sourceModel.wcetFunction.map((p, t) => p._1.getIdentifier + "-" + p._2.getIdentifier -> t).toString)

  val mznInputs =
    Map(
      "hyperPeriod"     -> MiniZincData(hyperPeriod.multiply(multiplier).getNumeratorAsLong),
      "nReactors"       -> MiniZincData(reactorsOrdered.length),
      "nReactions"      -> MiniZincData(reactionsOrdered.length),
      "nChannels"       -> MiniZincData(channelsOrdered.length),
      "nJobs"           -> MiniZincData(jobsOrdered.length),
      "nJobChannels"    -> MiniZincData(jobChannelsOrdered.length),
      "nPlatformElems"  -> MiniZincData(platformOrdered.length),
      "nReactionChains" -> MiniZincData(reactionChainsOrdered.length),
      "isFixedPriorityElem" -> MiniZincData(platformOrdered.map(p => {
        p match
          case pp: GenericProcessingModule =>
            sourceModel.platform.fixedPriorityPEs.contains(pp)
          case _ =>
            false
        // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.timeTriggeredPEs.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isTimeTriggeredElem" -> MiniZincData(platformOrdered.map(p => {
        p match
          case pp: GenericProcessingModule =>
            sourceModel.platform.timeTriggeredPEs.contains(pp)
          case _ =>
            false
        // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.timeTriggeredPEs.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isRoundRobinElem" -> MiniZincData(platformOrdered.map(p => {
        p match
          case pp: GenericProcessingModule =>
            sourceModel.platform.roundRobinPEs.contains(pp)
          case _ =>
            false
        // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.roundRobinPEs.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isProcessingElem" -> MiniZincData(platformOrdered.map(p => {
        p match
          case pp: GenericProcessingModule =>
            sourceModel.platform.hardware.processingElems.contains(pp)
          case _ => false
        // p.isInstanceOf[GenericProcessingModule] && sourceModel.platform.hardware.processingElems.contains(p.asInstanceOf[GenericProcessingModule]))
      })),
      "isCommunicationElem" -> MiniZincData(platformOrdered.map(p => {
        p match
          case pp: GenericDigitalInterconnect =>
            sourceModel.platform.hardware.communicationElems.contains(pp)
          case _ => false
        // p.isInstanceOf[GenericDigitalInterconnect] && sourceModel.platform.hardware.communicationElems.contains(p.asInstanceOf[GenericDigitalInterconnect]))
      })),
      "isMemoryElem" -> MiniZincData(platformOrdered.map(p => {
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
          reactorsOrdered.indexOf(
            sourceModel.reactorMinus.containmentFunction(r)
          ) + 1
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
        jobsOrdered.map(j => j.trigger.multiply(multiplier).getNumeratorAsLong)
      ),
      "jobDeadline" -> MiniZincData(
        jobsOrdered.map(j => j.deadline.multiply(multiplier).getNumeratorAsLong)
      ),
      "reactionDataSize" -> MiniZincData(
        reactionsOrdered.map(r => {
          val sizeFunction        = sourceModel.reactorMinus.sizeFunction
          val containmentFunction = sourceModel.reactorMinus.containmentFunction
          val a                   = containmentFunction(r)
          val rs =
            sourceModel.reactorMinus.reactions.filter(rr => rr != r && containmentFunction(rr) == a)
          sizeFunction(a) - rs.map(sizeFunction(_)).sum
        })
      ),
      "reactionWcet" -> MiniZincData(
        reactionsOrdered.map(r => {
          platformOrdered
            .map(p => {
              p match
                case pe: GenericProcessingModule =>
                  sourceModel.wcetFunction
                    .getOrElse((r, pe), BigFraction.ZERO)
                    .multiply(multiplier)
                    .doubleValue
                    .ceil
                case _ => 0
            })
        })
      ),
      "reactionUtilization" -> MiniZincData(
        reactionsOrdered.map(r => {
          platformOrdered
            .map(p => {
              p match
                case pe: GenericProcessingModule =>
                  sourceModel.wcetFunction
                    .getOrElse((r, pe), BigFraction.ZERO)
                    .divide(hyperPeriod)
                    .percentageValue
                    .ceil
                    .toLong
                case _ => 0
            })
        })
      ),
      "reactionCanBeExecuted" -> MiniZincData(
        reactionsOrdered.map(r => {
          platformOrdered
            .map(p => {
              p match
                case pe: GenericProcessingModule =>
                  sourceModel.wcetFunction.contains((r, pe))
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
      // "roundRobinElemsMinSlice" -> MiniZincData(
      //   platformOrdered
      //     .map(p => {
      //       p match
      //         case pe: GenericProcessingModule =>
      //           if (sourceModel.platform.roundRobinPEs.contains(pe))
      //             sourceModel.platform.schedulersFromPEs(pe) match
      //               case s: RoundRobinScheduler =>
      //                 s.getMinimumTimeSliceInCycles
      //               case _ => 0
      //           else 0
      //         case _ => 0
      //     })
      // ),
      "roundRobinElemsMaxSlices" -> MiniZincData(
        platformOrdered
          .map(p => {
            p match
              case pe: GenericProcessingModule =>
                if (sourceModel.platform.roundRobinPEs.contains(pe))
                  sourceModel.platform.schedulersFromPEs(pe) match
                    case s: RoundRobinScheduler =>
                      Math.floorDiv(s.getMaximumTimeSliceInCycles, s.getMinimumTimeSliceInCycles)
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
                        sourceModel.platform.hardware.bandWidthBitPerSec
                          .getOrElse((c, p), 0.toLong) * multiplier
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
      // "firstInChain" -> MiniZincData(
      //   reactionChainsOrdered.map((src, _) => reactionsOrdered.indexOf(src) + 1)
      // ),
      // "lastInChain" -> MiniZincData(
      //   reactionChainsOrdered.map((_, dst) => reactionsOrdered.indexOf(dst) + 1)
      // ),
      "objLambda" -> MiniZincData(0),
      "platformElemsSymmetryGroups" -> MiniZincData(
        platformOrdered.map(p =>
          p match {
            case pe: GenericProcessingModule =>
              symmetryGroupsOrdered.indexWhere(s => s.contains(pe)) + 1
            case _ => 0
          }
        )
      ),
      "jobHigherPriority" -> MiniZincData(
        jobsOrdered.map(j =>
          jobsOrdered.map(jj =>
            sourceModel.reactorMinus.jobGraph.jobPriorityPartialOrder.contains((j, jj))
          )
        )
      ),
      "jobInterferes" -> MiniZincData(
        jobsOrdered.map(j =>
          jobsOrdered.map(jj => sourceModel.reactorMinus.jobGraph.jobInterferes.contains((j, jj)))
        )
      )
    )

  def rebuildFromMznOutputs(
      output: Map[String, MiniZincData],
      originalModel: ForSyDeModel
  ): ForSyDeModel =
    ForSyDeModel()

  override def dominates(other: DecisionModel): Boolean =
    super.dominates(other) && (other match {
      case mzn: ReactorMinusAppMapAndSched => sourceModel == mzn
      case _                               => true
    })

  override val uniqueIdentifier = "ReactorMinusAppMapAndSchedMzn"

end ReactorMinusAppMapAndSchedMzn

package idesyde.identification.minizinc.models.reactor

import idesyde.identification.minizinc.interfaces.MiniZincForSyDeDecisionModel
import scala.io.Source
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.minizinc.interfaces.MiniZincData
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.moc.linguafranca.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.platform.runtime.RoundRobinScheduler
import spire.algebra.*
import spire.math.*
import idesyde.identification.forsyde.models.reactor.ReactorMinusAppMapAndSched

final case class ReactorMinusAppMapAndSchedMzn(val sourceModel: ReactorMinusAppMapAndSched)
    extends MiniZincForSyDeDecisionModel:

  val coveredVertexes = sourceModel.coveredVertexes

  var multiplier =
    sourceModel.reactorMinus.jobGraph.jobs
      .flatMap(j => Seq(j.trigger, j.deadline))
      .map(_.denominator)
      .reduce((d1, d2) => spire.math.lcm(d1, d2))

  while (
    sourceModel.wcetFunction.values
      .filter(v => v.numerator > 0L)
      .map(_ * multiplier)
      // .exists(v => v.getNumeratorAsLong / 1e3 < v.denominator)
      .exists(v => v.doubleValue < 1.0)
  ) {
    multiplier = multiplier * 10
  }

  // TODO: It seems like some solvers cant handle longs.. so we do this hack for now.
  var memoryMultipler: Long =
    (sourceModel.reactorMinus.reactors.map(sourceModel.reactorMinus.sizeFunction(_)) ++
      sourceModel.reactorMinus.channels.values.map(sourceModel.reactorMinus.sizeFunction(_)) ++
      sourceModel.reactorMinus.reactions.map(sourceModel.reactorMinus.sizeFunction(_)) ++
      sourceModel.platform.hardware.storageElems
        .map(_.getSpaceInBits.asInstanceOf[Long]))
      .filter(_ > 0L)
      .reduce((l1, l2) => spire.math.gcd(l1, l2))
  while (
    (sourceModel.reactorMinus.reactors.map(sourceModel.reactorMinus.sizeFunction(_)) ++
      sourceModel.reactorMinus.channels.values.map(sourceModel.reactorMinus.sizeFunction(_)) ++
      sourceModel.reactorMinus.reactions.map(sourceModel.reactorMinus.sizeFunction(_)) ++
      sourceModel.platform.hardware.storageElems.map(
        _.getSpaceInBits.toLong
      )
      ++ sourceModel.platform.hardware.minTraversalTimePerBit.flatten
        .map(t => t * multiplier / memoryMultipler)
        .map(_.ceil.toLong)).max > Integer.MAX_VALUE.toLong
  ) {
    memoryMultipler *= 10L
  }

  val hyperPeriod             = sourceModel.reactorMinus.hyperPeriod
  val reactionToJobs          = sourceModel.reactorMinus.jobGraph.jobs.groupBy(_.srcReaction)
  val reactorsOrdered         = sourceModel.reactorMinus.reactors.toSeq
  val reactionsOrdered        = sourceModel.reactorMinus.reactions.toSeq
  val channelsOrdered         = sourceModel.reactorMinus.channels.toSeq
  lazy val jobsOrdered        = sourceModel.reactorMinus.jobGraph.jobs.toSeq
  lazy val jobChannelsOrdered = sourceModel.reactorMinus.jobGraph.inChannels.toSeq
  val platformOrdered = sourceModel.platform.hardware.platformElements.toSeq.sortBy(p =>
    p match {
      case pe: GenericProcessingModule =>
        reactionsOrdered
          .map(r =>
            (sourceModel.wcetFunction
              .getOrElse((r, pe), Rational.zero)
              * multiplier).numerator.toLong
          )
          .sum / reactionsOrdered.size.toLong
      case _ => 0L
    }
  )
  lazy val reactionChainsOrdered = sourceModel.reactorMinus.unambigousEndToEndReactions.toSeq
  lazy val fixedLatenciesOrdered = sourceModel.reactorMinus.unambigousEndToEndFixedLatencies
  lazy val symmetryGroupsOrdered = sourceModel.computationallySymmetricGroups.toSeq
  val maxReactionInterferences =
    sourceModel.reactorMinus.maximalInterferencePoints.map(_._2.length).max

  val mznModel = Source.fromResource("minizinc/reactorminus_to_networkedHW.mzn").mkString
  // scribe.debug(platformOrdered.toString)
  // scribe.debug(reactionsOrdered.toString)
  // scribe.debug(sourceModel.wcetFunction.map((p, t) => p._1.getIdentifier + "-" + p._2.getIdentifier -> t).toString)

  val mznInputs =
    Map(
      "hyperPeriod" -> MiniZincData((hyperPeriod * multiplier).numerator.toLong),
      "nReactors"   -> MiniZincData(reactorsOrdered.length),
      "nReactions"  -> MiniZincData(reactionsOrdered.length),
      "nChannels"   -> MiniZincData(channelsOrdered.length),
      // "nJobs"           -> MiniZincData(jobsOrdered.length),
      // "nJobChannels"    -> MiniZincData(jobChannelsOrdered.length),
      "nPlatformElems"           -> MiniZincData(platformOrdered.length),
      "nReactionChains"          -> MiniZincData(reactionChainsOrdered.length),
      "maxReactionInterferences" -> MiniZincData(maxReactionInterferences),
      "minProcessingCores"       -> MiniZincData(sourceModel.minProcessingCores),
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
          case pp: GenericCommunicationModule =>
            sourceModel.platform.hardware.communicationElems.contains(pp)
          case _ => false
        // p.isInstanceOf[GenericCommunicationModule] && sourceModel.platform.hardware.communicationElems.contains(p.asInstanceOf[GenericCommunicationModule]))
      })),
      "isMemoryElem" -> MiniZincData(platformOrdered.map(p => {
        p match
          case pp: GenericMemoryModule =>
            sourceModel.platform.hardware.storageElems.contains(pp)
          case _ => false
        // p.isInstanceOf[GenericMemoryModule] && sourceModel.platform.hardware.storageElems.contains(p.asInstanceOf[GenericMemoryModule]))
      })),
      "reactorSize" -> MiniZincData(
        reactorsOrdered.map(a => sourceModel.reactorMinus.sizeFunction(a) / memoryMultipler)
      ),
      "containingReactor" -> MiniZincData(
        reactionsOrdered.map(r =>
          reactorsOrdered.indexOf(
            sourceModel.reactorMinus.containmentFunction(r)
          ) + 1
        )
      ),
      "channelSize" -> MiniZincData(
        channelsOrdered.map((rr, c) => sourceModel.reactorMinus.sizeFunction(c) / memoryMultipler)
      ),
      "channelSrc" -> MiniZincData(
        channelsOrdered.map((rr, c) => reactionsOrdered.indexOf(rr._1) + 1)
      ),
      "channelDst" -> MiniZincData(
        channelsOrdered.map((rr, c) => reactionsOrdered.indexOf(rr._2) + 1)
      ),
      "reactionDataSize" -> MiniZincData(
        reactionsOrdered.map(r => {
          val sizeFunction        = sourceModel.reactorMinus.sizeFunction
          val containmentFunction = sourceModel.reactorMinus.containmentFunction
          val a                   = containmentFunction(r)
          val rs =
            sourceModel.reactorMinus.reactions.filter(rr => rr != r && containmentFunction(rr) == a)
          (sizeFunction(a) - rs.map(sizeFunction(_)).sum) / memoryMultipler
        })
      ),
      "reactionLatestRelease" -> MiniZincData(
        reactionsOrdered
          .map(r => {
            reactionToJobs(r).map(j => (j.trigger * multiplier).numerator).max
          })
      ),
      "reactionRelativeDeadline" -> MiniZincData(
        reactionsOrdered
          .map(r => {
            reactionToJobs(r)
              .map(j => ((j.deadline - (j.trigger)) * (multiplier)).numerator)
              .min
          })
      ),
      "reactionInitialRelativeOffset" -> MiniZincData(
        reactionsOrdered.map(r => {
          reactionsOrdered.map(rr => {
            // given reactionsPriorityOrdering: Ordering[LinguaFrancaReaction] =
            //   sourceModel.reactorMinus.reactionsPriorityOrdering
            if r != rr && (sourceModel.reactorMinus.containsEdge(r, rr) || sourceModel.reactorMinus
                .containmentFunction(r) == sourceModel.reactorMinus.containmentFunction(rr))
            then
              // if reactionsPriorityOrdering.compare(r, rr) > 0 then
              reactionToJobs(r)
                .flatMap(j => {
                  reactionToJobs(rr)
                    .filter(jj => sourceModel.reactorMinus.jobGraph.containsEdge(j, jj))
                    .map(jj => {
                      ((jj.trigger - (j.trigger)) * (multiplier)).numerator
                    })
                })
                .min
            else -1
          })
        })
      ),
      "reactionWcet" -> MiniZincData(
        reactionsOrdered.map(r => {
          platformOrdered
            .map(p => {
              p match
                case pe: GenericProcessingModule =>
                  (sourceModel.wcetFunction
                    .getOrElse((r, pe), Rational.zero)
                    * (multiplier)).ceil.toDouble
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
                  (sourceModel.wcetFunction
                    .getOrElse((r, pe), Rational.zero)
                    / (hyperPeriod)).ceil.toLong
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
      "reactionPriorityLTEQ" -> MiniZincData(
        reactionsOrdered.map(r =>
          reactionsOrdered.map(rr => {
            given reactionOrdering: Ordering[LinguaFrancaReaction] =
              sourceModel.reactorMinus.reactionsPriorityOrdering
            reactionOrdering.compare(r, rr) <= 0
          })
        )
      ),
      "reactionReachesLeftToRight" -> MiniZincData(
        reactionsOrdered.map(r =>
          reactionsOrdered.map(rr => {
            sourceModel.reactorMinus.reactionsExtendedReachability.contains((r, rr))
          })
        )
      ),
      "reactionMaxNumberInterferences" -> MiniZincData(
        reactionsOrdered.map(r =>
          reactionsOrdered.map(rr => {
            sourceModel.reactorMinus.maximalInterferencePoints.getOrElse((r, rr), Seq.empty).size
          })
        )
      ),
      "reactionInterferencesStart" -> MiniZincData(
        reactionsOrdered.map(r =>
          reactionsOrdered.map(rr => {
            val seq = sourceModel.reactorMinus.maximalInterferencePoints
              .getOrElse((r, rr), Seq.empty)
              .sorted
            seq
              .map(t => (t * (multiplier)).numerator)
              .padTo(
                maxReactionInterferences,
                seq.maxOption.map(t => (t * multiplier).numerator).getOrElse(0L)
              )
          })
        )
      ),
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
            val path = sourceModel.platform.hardware.inclusiveDirectPaths(src)(dst)
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
                  case c: GenericCommunicationModule =>
                    pe match
                      case p: GenericProcessingModule =>
                        sourceModel.platform.hardware.bandWidthBitPerSecMatrix
                          .getOrElse((c, p), 0.toLong) * multiplier / memoryMultipler
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
                m.getSpaceInBits / memoryMultipler
              case _ => 0
          })
      ),
      "reactionChainStart" -> MiniZincData(
        reactionChainsOrdered.map((srcdst, _) => reactionsOrdered.indexOf(srcdst._1) + 1)
      ),
      "reactionChainEnd" -> MiniZincData(
        reactionChainsOrdered.map((srcdst, _) => reactionsOrdered.indexOf(srcdst._2) + 1)
      ),
      "reactionChainFixedLatency" -> MiniZincData(
        reactionChainsOrdered.map((srcdst, _) =>
          (fixedLatenciesOrdered
            .getOrElse(srcdst, Rational.zero)
            * (multiplier)).ceil.toLong
        )
      ),
      "objPercentage" -> MiniZincData(0),
      "platformElemsSymmetryGroups" -> MiniZincData(
        platformOrdered.map(p =>
          p match {
            case pe: GenericProcessingModule =>
              symmetryGroupsOrdered.indexWhere(s => s.contains(pe)) + 1
            case _ => 0
          }
        )
      )
    )

  def rebuildFromMznOutputs(
      output: Map[String, MiniZincData],
      originalModel: ForSyDeSystemGraph
  ): ForSyDeSystemGraph =
    ForSyDeSystemGraph()

  override def dominates[DesignModel](other: DecisionModel, designModel: DesignModel): Boolean =
    super.dominates(other, designModel) && (other match {
      case mzn: ReactorMinusAppMapAndSched => sourceModel == mzn
      case _                               => true
    })

  override val uniqueIdentifier = "ReactorMinusAppMapAndSchedMzn"

end ReactorMinusAppMapAndSchedMzn

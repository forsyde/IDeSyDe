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
import spire.math.Rational
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.Largest
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMedian
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax
import org.chocosolver.solver.search.strategy.strategy.FindAndProve
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import idesyde.identification.choco.interfaces.ChocoModelMixin

object ConMonitorObj extends IMonitorContradiction {

  def onContradiction(cex: ContradictionException): Unit = {
    println(cex.toString())
  }
}

final case class ChocoSDFToSChedTileHW(
    val slower: ChocoSDFToSChedTileHWSlowest
)(using Fractional[Rational])
    extends ChocoCPForSyDeDecisionModel
    with ChocoModelMixin(shouldLearnSignedClauses = false) {

  val chocoModel: Model = slower.chocoModel

  override val modelMinimizationObjectives: Array[IntVar] = slower.modelMinimizationObjectives

  //-----------------------------------------------------
  // BRANCHING AND SEARCH

  val listScheduling = CompactingMultiCoreSDFListScheduling(
    slower.dse.sdfApplications,
    slower.dse.wcets.map(ws => ws.map(w => w * slower.timeMultiplier).map(_.ceil.intValue)),
    slower.dse.platform.tiledDigitalHardware.minTraversalTimePerBit.map(arr =>
      arr.map(v => (v * slower.timeMultiplier).ceil.toInt)
    ),
    slower.sdfAnalysisModule.firingsInSlots,
    slower.sdfAnalysisModule.invThroughputs,
    slower.sdfAnalysisModule.globalInvThroughput
  )
  // chocoModel.getSolver().plugMonitor(listScheduling)
  chocoModel.getSolver().plugMonitor(ConMonitorObj)

  // breaking symmetries for speed
  private val firingVectors =
    (0 until slower.sdfAnalysisModule.sdfAndSchedulers.sdfApplications.sdfRepetitionVectors.sum)
      .map(s =>
        chocoModel.sum(
          s"allOnSlot($s)",
          slower.sdfAnalysisModule.firingsInSlots.flatMap(pAndSVec =>
            pAndSVec.map(sVec => sVec(s))
          ): _*
        )
      )
      .toArray
  for (
    s <-
      0 until (slower.sdfAnalysisModule.sdfAndSchedulers.sdfApplications.sdfRepetitionVectors.sum - 1)
  ) {
    chocoModel.ifThen(
      firingVectors(s).eq(0).decompose(),
      firingVectors(s + 1).eq(0).decompose()
      // firingVectors(s + 1).eq(0).decompose()
    )
  }

  // breaking platform symmetries
  // val mappingsPerPe = slower.dse.platform.schedulerSet.map(p => chocoModel.count(s"mappedToPe($p)", p, slower.memoryMappingModule.processesMemoryMapping:_*))
  // slower.dse.platform.tiledDigitalHardware.symmetricTileGroups
  //   .maxByOption(_.size)
  //   .foreach(group => {
  //     for (
  //       a     <- slower.dse.sdfApplications.actorsSet;
  //       s     <- slower.sdfAnalysisModule.slotRange;
  //       p     <- group;
  //       other <- group - p;
  //       others = (group - p - other)
  //       if others.size > 2
  //     ) {
  //       chocoModel.ifThen(
  //         chocoModel.and(
  //           chocoModel.arithm(slower.sdfAnalysisModule.firingsInSlots(a)(p)(s), "=", 0),
  //           chocoModel.arithm(slower.sdfAnalysisModule.firingsInSlots(a)(other)(s), "=", 0)
  //         ),
  //         chocoModel.sum(others.map(slower.sdfAnalysisModule.firingsInSlots(a)(_)(s)).toArray, "=", 0)
  //       )
  //     }
  //     // chocoModel.decreasing(group.toArray.sorted.map(tile => mappingsPerPe(tile)), 0).post()
  //     // val lowestNumbered = group.min
  //     // for (other <- group.filter(_ != lowestNumbered)) {
  //     //   chocoModel.arithm(mappingsPerPe(lowestNumbered), ">=", mappingsPerPe(other)).post()
  //     // }
  //   })

  override val strategies: Array[AbstractStrategy[? <: Variable]] = Array(
    listScheduling,
    Search.minDomLBSearch(slower.sdfAnalysisModule.invThroughputs: _*),
    Search.minDomLBSearch(slower.sdfAnalysisModule.slotStartTime.flatten: _*),
    Search.minDomLBSearch(slower.sdfAnalysisModule.slotFinishTime.flatten: _*),
    Search.minDomLBSearch(slower.tileAnalysisModule.numVirtualChannelsForProcElem.flatten: _*),
    Search.minDomLBSearch(slower.tileAnalysisModule.messageIsCommunicated.flatten.flatten: _*),
    Search.minDomLBSearch(slower.tileAnalysisModule.messageTravelDuration.flatten.flatten: _*)
  ) //++ slower.strategies

  //---------

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph =
    slower.rebuildFromChocoOutput(output)

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW"

  def coveredVertexes: Iterable[Vertex] = slower.coveredVertexes

}

object ChocoSDFToSChedTileHW {

  def identifyFromAny(
      model: Any,
      identified: scala.collection.Iterable[DecisionModel]
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW] =
    ForSyDeIdentificationRule.identifyWrapper(model, identified, identifyFromForSyDe)

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW] = {
    identified
      .find(m => m.isInstanceOf[ChocoSDFToSChedTileHWSlowest])
      .map(m => m.asInstanceOf[ChocoSDFToSChedTileHWSlowest])
      .map(slower => identFromForSyDeWithDeps(model, slower))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      slower: ChocoSDFToSChedTileHWSlowest
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHW(slower))
  }

}

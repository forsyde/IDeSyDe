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
import idesyde.implicits.forsyde.given_Fractional_Rational
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.Largest
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMedian
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax
import org.chocosolver.solver.search.strategy.strategy.FindAndProve
import org.chocosolver.solver.constraints.Constraint

final case class ChocoSDFToSChedTileHW(
    val slower:  ChocoSDFToSChedTileHWSlowest
)(using Fractional[Rational])
    extends ChocoCPForSyDeDecisionModel {

  val chocoModel: Model = slower.chocoModel

  override def modelObjectives: Array[IntVar] = slower.modelObjectives

  //-----------------------------------------------------
  // BRANCHING AND SEARCH

  val listScheduling = CommAwareMultiCoreSDFListScheduling(
      slower.dse.sdfApplications.actors.zipWithIndex.map((a, i) => slower.dse.sdfApplications.sdfRepetitionVectors(i)),
      slower.dse.sdfApplications.sdfBalanceMatrix,
      slower.dse.sdfApplications.initialTokens,
      slower.dse.wcets.map(ws => ws.map(w => w * slower.timeMultiplier).map(_.ceil.intValue)),
      slower.tileAnalysisModule.messageTravelDuration,
      slower.sdfAnalysisModule.firingsInSlots,
      slower.sdfAnalysisModule.invThroughputs
    )
  chocoModel.getSolver().plugMonitor(listScheduling)
  val tokensPropagator = SDFLikeTokensPropagator(
    slower.dse.sdfApplications.actors.zipWithIndex.map((a, i) => slower.dse.sdfApplications.sdfRepetitionVectors(i)),
    slower.dse.sdfApplications.sdfBalanceMatrix,
    slower.dse.sdfApplications.initialTokens,
    slower.sdfAnalysisModule.firingsInSlots,
    slower.sdfAnalysisModule.tokensBefore,
    slower.sdfAnalysisModule.tokensAfter
  )
  chocoModel.post(
    new Constraint(
      "global_sas_sdf_prop",
      tokensPropagator
    )
  )

  override def strategies: Array[AbstractStrategy[? <: Variable]] = slower.strategies :+ listScheduling

  //---------

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = slower.rebuildFromChocoOutput(output)

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW"

  def coveredVertexes: Iterable[Vertex] = slower.coveredVertexes

}

object ChocoSDFToSChedTileHW extends ForSyDeIdentificationRule[ChocoSDFToSChedTileHW] {
  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ): IdentificationResult[ChocoSDFToSChedTileHW] = {
    identified
      .find(m => m.isInstanceOf[ChocoSDFToSChedTileHWSlowest])
      .map(m => m.asInstanceOf[ChocoSDFToSChedTileHWSlowest])
      .map(slower => identFromForSyDeWithDeps(model, slower))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      slower: ChocoSDFToSChedTileHWSlowest
  ): IdentificationResult[ChocoSDFToSChedTileHW] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHW(slower))
  }

}

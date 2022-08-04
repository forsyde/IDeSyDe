package idesyde.identification.models.choco.sdf

import idesyde.identification.models.mixed.SDFToSchedTiledHW
import idesyde.identification.interfaces.ChocoCPForSyDeDecisionModel
import org.chocosolver.solver.Model
import forsyde.io.java.core.Vertex
import org.chocosolver.solver.Solution
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.ForSyDeIdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.models.choco.ManyProcessManyMessageMemoryConstraintsMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar

final case class ChocoSDFToSChedTileHW(
    val dse: SDFToSchedTiledHW
) extends ChocoCPForSyDeDecisionModel
    with ManyProcessManyMessageMemoryConstraintsMixin {

  def chocoModel: Model = Model()

  // section for time multiplier calculation
  val timeValues =
    (dse.wcets.flatten)
  var timeMultiplier = 1L
  while (
    timeValues
      .map(_.multiply(timeMultiplier))
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

  def dataSize: Array[Int] =
    dse.sdfApplications.messagesMaxSizes.map(l => (l / memoryDivider).toInt)

  def processSize: Array[Int] =
    dse.sdfApplications.processSizes.map(l => (l / memoryDivider).toInt)

  //-----------------------------------------------------
  // Decision variables
  def messagesMemoryMapping: Array[Array[BoolVar]] = dse.sdfApplications.channels.map(c =>
    dse.platform.tiledDigitalHardware.tileSet.map(tile =>
      chocoModel.boolVar(s"${c.getIdentifier()}_${tile}_m")
    )
  )

  def processesMemoryMapping: Array[Array[BoolVar]] = dse.sdfApplications.actors.map(a =>
    dse.platform.tiledDigitalHardware.tileSet.map(tile =>
      chocoModel.boolVar(s"${a.getIdentifier()}_${tile}_m")
    )
  )

  //---------

  def memoryUsage: Array[IntVar] = dse.platform.tiledDigitalHardware.memories
    .map(m => (m, (m.getSpaceInBits() / memoryDivider).toInt))
    .map((m, s) => chocoModel.intVar(s"${m.getIdentifier()}_u", 0, s, true))

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

  // - mixed constraints
  postManyProcessManyMessageMemoryConstraints()

  //---------

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val mappings = messagesMemoryMapping.map(vs => vs.map(v => if (output.getIntVal(v) > 0) then true else false))
    val schedulings = processesMemoryMapping.map(vs => vs.map(v => if (output.getIntVal(v) > 0) then true else false))
    dse.addMappingsAndRebuild(mappings, schedulings)
  }

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW"

  def coveredVertexes: Iterable[Vertex] = dse.coveredVertexes

}

object ChocoSDFToSChedTileHW extends ForSyDeIdentificationRule[ChocoSDFToSChedTileHW] {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
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

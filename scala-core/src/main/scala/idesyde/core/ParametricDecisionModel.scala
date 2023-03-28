package idesyde.core

import idesyde.core.headers.DecisionModelHeader
import upickle.default.*
import idesyde.core.DecisionModel
import idesyde.core.headers.LabelledArcWithPorts

final case class ParametricDecisionModel[B: ReadWriter](
    override val header: DecisionModelHeader,
    val body: B
) extends DecisionModel {

  type ElementT = String

  type ElementRelationT = LabelledArcWithPorts

  lazy val coveredElements = header.covered_elements.toSet

  lazy val coveredElementRelations = header.covered_relations.toSet

  def elementID(elem: String): String = elem

  def elementRelationID(rel: LabelledArcWithPorts): LabelledArcWithPorts = rel

  def bodyAsText: String = write(body)

  def bodyAsBinary: Array[Byte] = writeBinary(body)

  def headerAsText: String = write(header)

  def headerAsBinary: Array[Byte] = writeBinary(header)

  def uniqueIdentifier: String = header.category
}

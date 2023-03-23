package idesyde.core

import idesyde.core.headers.DecisionModelHeader
import upickle.default.*
import idesyde.identification.DecisionModel
import idesyde.core.headers.LabelledArcWithPorts

final case class ParametricDecisionModel[B : ReadWriter](val header: DecisionModelHeader, val body: B) extends DecisionModel {
    
    type ElementT = String

    type ElementRelationT = LabelledArcWithPorts

    lazy val coveredElements = header.covered_elements.toSet

    lazy val coveredElementRelations = header.covered_relations.toSet

    def elementID(elem: String): String = elem

    def elementRelationID(rel: LabelledArcWithPorts): String = rel.toString()

    def bodyAsText: String = write(body)

    def uniqueIdentifier: String = header.category
}

package idesyde.core

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.ExplorerHeader
import idesyde.core.ExplorationCriteria

final case class ExplorationCombinationDescription(
    val explorer_unique_identifier: String,
    val can_explore: Boolean,
    val is_complete: Boolean,
    val competitiveness: Double,
    val target_objectives: Set[String],
    val additional_numeric_properties: Map[String, Double]
) derives ReadWriter {

//   lazy val criterias: Map[ExplorationCriteria, Double] = explorer
//     .availableCriterias(decisionModel)
//     .map(c => c -> explorer.criteriaValue(decisionModel, c))
//     .toMap

//   lazy val criteriasAsMap: Map[String, Double] = criterias.map((c, d) => c.identifier -> d).toMap

//   def header: ExplorationCombinationHeader =
//     ExplorationCombinationHeader(explorer.header, decisionModel.header, criteriasAsMap)

  def asText: String = write(this)

  def asBinary: Array[Byte] = writeBinary(this)

  def `<?>`(o: ExplorationCombinationDescription): Char =
    ExplorationCombinationDescription.partialOrdering.tryCompare(this, o) match {
      case Some(1)  => '>'
      case Some(0)  => '='
      case Some(-1) => '<'
      case _        => '?'
    }

}

object ExplorationCombinationDescription {

  def impossible(uniqueIdentifier: String) =
    ExplorationCombinationDescription(uniqueIdentifier, false, false, 1.0, Set(), Map())

  val partialOrdering = new PartialOrdering[ExplorationCombinationDescription] {
    def lteq(x: ExplorationCombinationDescription, y: ExplorationCombinationDescription): Boolean =
      tryCompare(x, y) match {
        case Some(-1) | Some(0) => true
        case _                  => false
      }

    def tryCompare(
        x: ExplorationCombinationDescription,
        y: ExplorationCombinationDescription
    ): Option[Int] = if (
      x.is_complete == y.is_complete &&
      Math.abs(x.competitiveness - y.competitiveness) <= 0.001 &&
      x.target_objectives == y.target_objectives &&
      x.additional_numeric_properties.keySet == y.additional_numeric_properties.keySet
    ) {
      var isgt = true
      var islt = true
      for ((k, v) <- x.additional_numeric_properties) {
        isgt = isgt && v > y.additional_numeric_properties(k)
        islt = islt && v < y.additional_numeric_properties(k)
      }
      Some(
        if (isgt) 1 else if (islt) -1 else 0
      )
    } else None
  }

}

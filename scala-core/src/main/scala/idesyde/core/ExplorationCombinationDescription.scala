package idesyde.core

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.ExplorationCombinationHeader
import idesyde.core.headers.ExplorerHeader
import idesyde.core.ExplorationCriteria

final case class ExplorationCombinationDescription(
    val explorer_unique_identifier: String,
    val decision_model_unique_identifier: String,
    val can_explore: Boolean,
    val properties: Map[String, Double]
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

  def impossible(uniqueIdentifier: String, decisionModelIdentifier: String) =
    ExplorationCombinationDescription(uniqueIdentifier, decisionModelIdentifier, false, Map())

  val partialOrdering = new PartialOrdering[ExplorationCombinationDescription] {
    def lteq(x: ExplorationCombinationDescription, y: ExplorationCombinationDescription): Boolean =
      tryCompare(x, y) match {
        case Some(-1) | Some(0) => true
        case _                  => false
      }

    def tryCompare(
        x: ExplorationCombinationDescription,
        y: ExplorationCombinationDescription
    ): Option[Int] = if (x.properties.keySet == y.properties.keySet) {
      var isgt = true
      var islt = true
      for ((k, v) <- x.properties) {
        isgt = isgt && v > y.properties(k)
        islt = islt && v < y.properties(k)
      }
      Some(
        if (isgt) 1 else if (islt) -1 else 0
      )
    } else None
  }

}

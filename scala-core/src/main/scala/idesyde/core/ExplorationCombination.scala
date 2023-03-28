package idesyde.core

import idesyde.core.Explorer
import idesyde.core.headers.ExplorationCombinationHeader
import idesyde.core.headers.ExplorerHeader
import idesyde.exploration.ExplorationCriteria

final case class ExplorationCombination(
    val explorer: Explorer,
    val decisionModel: DecisionModel
) {

    lazy val criterias: Map[ExplorationCriteria, Double] = explorer.availableCriterias(decisionModel).map(c => c -> explorer.criteriaValue(decisionModel, c)).toMap

    lazy val criteriasAsMap: Map[String, Double] = criterias.map((c, d) => c.identifier -> d).toMap

    def header: ExplorationCombinationHeader = ExplorationCombinationHeader(explorer.header, decisionModel.header, criteriasAsMap)

}

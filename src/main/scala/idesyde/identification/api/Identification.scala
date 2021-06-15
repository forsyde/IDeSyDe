package idesyde.identification.api

import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

object Identification {


    def identifyDecisionModels(model: ForSyDeModel)(using rules: Set[IdentificationRule[? <: DecisionModel]]): Set[? <: DecisionModel] = {
        var identified: Set[DecisionModel] = Set()
        var activeRules = rules
        val maxIters = rules.size * modelTraitCombinations(model).size
        var iters = 0
        while (activeRules.size > 0 && iters < maxIters) {
            val ruleResults = activeRules.map(r => (r, r.identify(model, identified)))
            identified = ruleResults.filter((r, res) => !res._2.isEmpty).map((r, res) => res._2.get)
            activeRules = ruleResults.filter((r, res) => res._1).map((r, res) => r)
            iters += 1
        }
        identified
    }

    protected def modelTraitCombinations(model: ForSyDeModel): Set[Set[VertexTrait]] =
        model.vertexSet.stream.map(_.vertexTraits.asScala.toSet).collect(Collectors.toSet).asScala.toSet

}

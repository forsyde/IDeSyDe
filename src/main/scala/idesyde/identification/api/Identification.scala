package idesyde.identification.api

import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait
import java.util.stream.Collectors
import forsyde.io.java.core.Trait
import scala.collection.mutable.HashSet
import idesyde.identification.rules.SDFAppIdentificationRule
import idesyde.identification.rules.ReactorMinusIdentificationRule
import idesyde.identification.rules.TimeTriggeredReactorMinusIdent
import idesyde.identification.rules.EnrichReactorMemoryMinusIdent
import idesyde.identification.rules.EnrichReactorSignalMemoryMinusIdent

object Identification {

    var defaultRegisteredRules: Set[IdentificationRule[? <: DecisionModel]] = Set[IdentificationRule[? <: DecisionModel]](
        SDFAppIdentificationRule(),
        ReactorMinusIdentificationRule(),
        TimeTriggeredReactorMinusIdent(),
        EnrichReactorMemoryMinusIdent(),
        EnrichReactorSignalMemoryMinusIdent()
    )

    def identifyDecisionModels(model: ForSyDeModel)(using rules: Set[IdentificationRule[? <: DecisionModel]]): Set[? <: DecisionModel] = {
        var identified: Set[DecisionModel] = Set()
        var activeRules = rules
        val maxIters = rules.size * countTraits(model)
        var iters = 0
        while (activeRules.size > 0 && iters < maxIters) {
            val ruleResults = activeRules.map(r => (r, r.identify(model, identified)))
            identified = identified.union(ruleResults.filter((r, res) => !res._2.isEmpty).map((r, res) => res._2.get).toSet) 
            identified = identified.filter(m => !identified.exists(other => other != m && other.dominates(m)))
            activeRules = ruleResults.filter((r, res) => !res._1).map(_._1)
            iters += 1
        }
        identified
    }

    protected def countTraits(model: ForSyDeModel): Integer =
        model.vertexSet.stream.flatMap(_.getTraits.stream).distinct.count.toInt

}

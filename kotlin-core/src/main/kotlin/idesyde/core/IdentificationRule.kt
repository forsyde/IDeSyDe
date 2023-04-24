package idesyde.core

interface IdentificationRule<out T: DecisionModel> {

    fun usesDesignModels(): Boolean = true

    fun usesDecisionModels(): Boolean = true

    fun usesParticularDecisionModels(): List<String> = listOf()

    fun partiallyIdentify(designModels: Set<DesignModel>, decisionModels: Set<idesyde.core.DecisionModel>): Set<T>
}
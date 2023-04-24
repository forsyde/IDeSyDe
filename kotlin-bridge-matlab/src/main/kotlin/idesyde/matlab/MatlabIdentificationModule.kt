package idesyde.matlab

import idesyde.core.*
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.headers.DesignModelHeader

object MatlabIdentificationModule : IdentificationModule {
    override fun uniqueIdentifier(): String = "MatlabIdentificationModule"

    override fun decisionHeaderToModel(m: DecisionModelHeader): DecisionModel? {
        TODO("Not yet implemented")
    }

    override fun designHeaderToModel(m: DesignModelHeader): DesignModel? {
        TODO("Not yet implemented")
    }

    override fun reverseIdentificationRules(): Set<(Set<DesignModel>, Set<DecisionModel>) -> Set<DesignModel>> {
        TODO("Not yet implemented")
    }

    override fun identificationRules(): Set<IdentificationRule<DecisionModel>> = setOf(
        CommunicatingAndTriggeredReactiveWorkloadIRule()
    )

    @JvmStatic
    fun main(args: Array<String>): Unit {
        standaloneIdentificationModule(args)
    }
}
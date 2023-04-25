package idesyde.common

import idesyde.core.*

object KCommonIdentificationModule : IdentificationModule {
    override fun uniqueIdentifier(): String = "KCommonIdentificationModule"

    override fun decisionHeaderToModel(m: DecisionModelHeader): DecisionModel? = null

    override fun designHeaderToModel(m: DesignModelHeader): DesignModel? {
        TODO("Not yet implemented")
    }

    override fun reverseIdentificationRules(): Set<(Set<DesignModel>, Set<DecisionModel>) -> Set<DesignModel>> {
        TODO("Not yet implemented")
    }

    override fun identificationRules(): Set<IdentificationRule<DecisionModel>> {
        TODO("Not yet implemented")
    }


}
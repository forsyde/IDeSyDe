package idesyde.identification

trait IntegrationRule[M <: DesignModel] extends Function2[DesignModel, DecisionModel, Option[M]] {}

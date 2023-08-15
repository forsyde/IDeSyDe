package idesyde.core

/** Enumeration to mark additional information in identification rules.
  *
  * This enumeration encapsulates an identification rule and signals to the identification loop
  * whether the function depends only in the given decision models or the decision models.
  *
  * @param identRule
  *   the encapsulated identification rule
  */
enum MarkedIdentificationRule(
    val identRule: Function2[Set[DesignModel], Set[
      DecisionModel
    ], (Set[? <: DecisionModel], Set[String])]
) extends Function2[Set[DesignModel], Set[DecisionModel], (Set[? <: DecisionModel], Set[String])] {
  def apply(v1: Set[DesignModel], v2: Set[DecisionModel]): (Set[? <: DecisionModel], Set[String]) =
    identRule(v1, v2)

  /** @see [[idesyde.identification.MarkedIdentificationRule]] */
  case DesignModelOnlyIdentificationRule(
      func: Function2[Set[DesignModel], Set[DecisionModel], (Set[? <: DecisionModel], Set[String])]
  ) extends MarkedIdentificationRule(func)

  /** @see [[idesyde.identification.MarkedIdentificationRule]] */
  case DecisionModelOnlyIdentificationRule(
      func: Function2[Set[DesignModel], Set[DecisionModel], (Set[? <: DecisionModel], Set[String])]
  ) extends MarkedIdentificationRule(func)

  /** @see [[idesyde.identification.MarkedIdentificationRule]] */
  case SpecificDecisionModelOnlyIdentificationRule(
      func: Function2[Set[DesignModel], Set[DecisionModel], (Set[? <: DecisionModel], Set[String])],
      val decisionModelIdentifiers: Set[String] = Set.empty
  ) extends MarkedIdentificationRule(func)
}

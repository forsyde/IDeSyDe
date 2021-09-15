package idesyde.identification.models.reactor

import forsyde.io.java.typed.viewers.LinguaFrancaReaction

import org.apache.commons.math3.fraction.BigFraction

final case class ReactionJob (val srcReaction: LinguaFrancaReaction, val trigger: BigFraction, val deadline: BigFraction):

    override def toString: String = s"(${srcReaction.getIdentifier}, ${trigger.toString}, ${deadline.toString})"

    // override def hashCode: Int = srcReaction.hashCode + trigger.hashCode + deadline.hashCode

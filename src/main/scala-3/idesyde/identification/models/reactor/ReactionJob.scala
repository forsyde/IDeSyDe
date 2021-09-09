package idesyde.identification.models.reactor

import forsyde.io.java.typed.viewers.LinguaFrancaReaction

import org.apache.commons.math3.fraction.Fraction

final case class ReactionJob (val srcReaction: LinguaFrancaReaction, val trigger: Fraction, val deadline: Fraction)

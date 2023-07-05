package idesyde.identification.forsyde.models.reactor



import forsyde.io.java.typed.viewers.moc.linguafranca.LinguaFrancaReaction
import spire.math.Rational

final case class ReactionJob(
    val srcReaction: LinguaFrancaReaction,
    val trigger: Rational,
    val deadline: Rational
):

  override def toString: String =
    s"(${srcReaction.getIdentifier}, ${trigger.toString}, ${deadline.toString})"

  def compareTo(o: ReactionJob)(using order: Ordering[ReactionJob]) = order.compare(this, o)

  infix def <(o: ReactionJob)(using order: Ordering[ReactionJob]) = compareTo(o) < 0

  infix def >(o: ReactionJob)(using order: Ordering[ReactionJob]) = compareTo(o) > 0

  def interferes(o: ReactionJob) =
    deadline.compareTo(o.trigger) > 0 && deadline.compareTo(o.deadline) < 0 ||
    o.deadline.compareTo(trigger) > 0 && o.deadline.compareTo(deadline) < 0

// override def hashCode: Int = srcReaction.hashCode + trigger.hashCode + deadline.hashCode

package idesyde.identification.models.reactor

import forsyde.io.java.typed.viewers.moc.linguafranca.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.moc.linguafranca.LinguaFrancaReactor


enum ReactionChannel(val src: ReactionJob, val dst: ReactionJob):
  case CommReactionChannel(
      override val src: ReactionJob,
      override val dst: ReactionJob,
      val channel: LinguaFrancaSignal
  ) extends ReactionChannel(src, dst)
  case StateReactionChannel(
      override val src: ReactionJob,
      override val dst: ReactionJob,
      val reactor: LinguaFrancaReactor
  ) extends ReactionChannel(src, dst)


end ReactionChannel

object ReactionChannel:

  def apply(src: ReactionJob, dst: ReactionJob, extra: LinguaFrancaSignal | LinguaFrancaReactor): ReactionChannel =
    extra match {
      case c: LinguaFrancaSignal  => CommReactionChannel(src, dst, c)
      case a: LinguaFrancaReactor => StateReactionChannel(src, dst, a)
    }

end ReactionChannel

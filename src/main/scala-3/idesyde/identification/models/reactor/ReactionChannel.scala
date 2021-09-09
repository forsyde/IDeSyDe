package idesyde.identification.models.reactor

import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.LinguaFrancaReactor

enum ReactionChannel:
  case CommReactionChannel(
      val src: ReactionJob,
      val dst: ReactionJob,
      val channel: LinguaFrancaSignal
  )
  case StateReactionChannel(
      val src: ReactionJob,
      val dst: ReactionJob,
      val reactor: LinguaFrancaReactor
  )

object ReactionChannel:

  def apply(src: ReactionJob, dst: ReactionJob, extra: LinguaFrancaSignal | LinguaFrancaReactor): ReactionChannel =
    extra match {
      case c: LinguaFrancaSignal  => CommReactionChannel(src, dst, c)
      case a: LinguaFrancaReactor => StateReactionChannel(src, dst, a)
    }

end ReactionChannel

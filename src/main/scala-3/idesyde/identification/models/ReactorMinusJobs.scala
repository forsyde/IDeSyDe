package idesyde.identification.models

import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect

type ReactorJobType = (LinguaFrancaReaction, Int, Int)
type JobChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaSignal)
type ResourceType   = GenericProcessingModule | GenericDigitalStorage | GenericDigitalInterconnect

final case class ReactorMinusJobs(
    val jobs: Set[ReactorJobType],
    val channels: Map[(ReactorJobType, ReactorJobType), LinguaFrancaSignal]
) {
    
}

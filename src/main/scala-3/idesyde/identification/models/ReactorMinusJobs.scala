package idesyde.identification.models

import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import idesyde.identification.interfaces.DecisionModel
import org.apache.commons.math3.fraction.Fraction
import forsyde.io.java.typed.viewers.LinguaFrancaReactor

type ReactorJobType = (LinguaFrancaReaction, Fraction, Fraction)
type CommChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaSignal)
type StateChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaReactor)
type ResourceType   = GenericProcessingModule | GenericDigitalStorage | GenericDigitalInterconnect

final case class ReactorMinusJobs(
    val periodicJobs: Set[ReactorJobType],
    val pureJobs: Set[ReactorJobType],
    val pureChannels: Set[CommChannelType],
    val stateChannels: Set[StateChannelType],
    val outerStateChannels: Set[StateChannelType],
    val reactorMinusApp: ReactorMinusApplication
) extends DecisionModel {
    
    def coveredVertexes() = reactorMinusApp.coveredVertexes()

    def coveredEdges() = reactorMinusApp.coveredEdges()
}

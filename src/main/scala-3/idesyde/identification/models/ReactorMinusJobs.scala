package idesyde.identification.models

import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.GenericProcessingModule
import forsyde.io.java.typed.viewers.GenericDigitalStorage
import forsyde.io.java.typed.viewers.GenericDigitalInterconnect
import idesyde.identification.interfaces.DecisionModel
import org.apache.commons.math3.fraction.Fraction

type ReactorJobType = (LinguaFrancaReaction, Fraction, Fraction)
type JobChannelType = (ReactorJobType, ReactorJobType, LinguaFrancaSignal)
type ResourceType   = GenericProcessingModule | GenericDigitalStorage | GenericDigitalInterconnect

final case class ReactorMinusJobs(
    val jobs: Set[ReactorJobType],
    val channels: Map[(ReactorJobType, ReactorJobType), LinguaFrancaSignal],
    val reactorMinusApp: ReactorMinusApplication
) extends DecisionModel {
    
    def coveredVertexes() = reactorMinusApp.coveredVertexes()

    def coveredEdges() = reactorMinusApp.coveredEdges()
}

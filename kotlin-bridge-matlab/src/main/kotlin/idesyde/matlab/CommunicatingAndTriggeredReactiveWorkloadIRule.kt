package idesyde.matlab

import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.core.IdentificationRule

class CommunicatingAndTriggeredReactiveWorkloadIRule : IdentificationRule<CommunicatingAndTriggeredReactiveWorkload> {

    override fun usesDecisionModels(): Boolean = false

    override fun partiallyIdentify(
        designModels: Set<DesignModel>,
        decisionModels: Set<DecisionModel>
    ): Set<CommunicatingAndTriggeredReactiveWorkload> {
        val simulinks = designModels.filter { it is SimulinkReactiveDesignModel }.map { it as SimulinkReactiveDesignModel }
        return setOf(
            CommunicatingAndTriggeredReactiveWorkload(
                tasks = simulinks.flatMap { it.processes } + simulinks.flatMap { it.delays },
                taskSizes =
                simulinks.flatMap { s -> s.processes.map { s.processesSizes[it] ?: 0L } } +
                        simulinks.flatMap { s -> s.delays.map { s.delaysSizes[it] ?: 0L} },
                taskComputationalNeeds =
                simulinks.flatMap { s -> s.processes.map { s.processesOperations[it] ?: mapOf() } } +
                        simulinks.flatMap { s -> s.delays.map { s.delaysOperations[it] ?: mapOf() } },
                dataChannels =
                simulinks.flatMap { it.links }.map { it.toString() },
                dataChannelSizes = simulinks.flatMap { it.links }.map { it.dataSize },
                dataGraphSrc = simulinks.flatMap { it.links }.map { it.src },
                dataGraphDst = simulinks.flatMap { it.links }.map { it.dst },
                dataGraphMessageSize = simulinks.flatMap { it.links }.map { it.dataSize },
                periodicSources = simulinks.flatMap { it.sources },
                periods  = simulinks.flatMap { s -> s.sources.map { s.sourcesPeriods[it] ?: 0.0} },
                offsets = simulinks.flatMap { s -> s.sources.map { 0.0 } },
                upsamples = listOf(),
                upsampleRepetitiveHolds = listOf(),
                upsampleInitialHolds = listOf(),
                downsamples = listOf(),
                downampleRepetitiveSkips = listOf(),
                downampleInitialSkips = listOf(),
                triggerGraphSrc = simulinks.flatMap { it.links }.map { it.src },
                triggerGraphDst = simulinks.flatMap { it.links }.map { it.dst },
                hasORTriggerSemantics = setOf()
            )
        )
    }
}
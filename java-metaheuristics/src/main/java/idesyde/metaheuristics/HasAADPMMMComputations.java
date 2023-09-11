package idesyde.metaheuristics;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore;

interface HasAADPMMMComputations {

    default double processFetchTime(AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore decisionModel,
            String processName) {
        var hostSched = decisionModel.processesToRuntimeScheduling().get(processName);
        var hostPe = decisionModel.partitionedMemMappableMulticore().runtimes().runtimeHost().get(hostSched);
        var hostMe = decisionModel.processesToMemoryMapping().get(processName);
        // var minimumBW =
        // decisionModel.partitionedMemMappableMulticore().hardware().preComputedPaths()
        // .get(hostMe).get(hostPe)
        // .stream().mapToInt(ce ->
        // decisionModel.partitionedMemMappableMulticore().hardware().
        // decisionModel.processingElementsToRoutersReservations().get(hostPe).get(ce))
        // .min().orElse(0);
        return 0.0;
    }
}

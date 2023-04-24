package idesyde.matlab

import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.LabelledArcWithPorts

data class SimulinkReactiveDesignModel(
    val processes: Set<String>,
    val processesSizes: Map<String, Long>,
    val delays: Set<String>,
    val delaysSizes: Map<String, Long>,
    val sources: Set<String>,
    val sourcesSizes: Map<String, Long>,
    val sourcesPeriods: Map<String, Double>,
    val constants: Set<String>,
    val sinks: Set<String>,
    val sinksSizes: Map<String, Long>,
    val sinksDeadlines: Map<String, Double>,
    val processesOperations: Map<String, Map<String, Map<String, Long>>>,
    val delaysOperations: Map<String, Map<String, Map<String, Long>>>,
    val links: Set<SimulinkArc>
) : DesignModel {
    override fun header(): DesignModelHeader =
        DesignModelHeader(
            category = "SimulinkReactiveDesignModel",
            elements = (processes + delays + sources + constants + sinks).toList(),
            modelPaths = listOf(),
            relations = links.map { l ->
                LabelledArcWithPorts(
                    src = l.src,
                    dst = l.dst,
                    srcPort = l.srcPort,
                    dstPort = l.dstPort,
                    label = l.dataSize.toString()
                )
            }

        )
}


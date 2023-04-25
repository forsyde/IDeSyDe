package idesyde.common

import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack
import kotlinx.serialization.json.Json

@Serializable
data class CommunicatingAndTriggeredReactiveWorkload(
    val tasks: List<String>,
    val taskSizes: List<Long>,
    val taskComputationalNeeds: List<Map<String, Map<String, Long>>>,
    val dataChannels: List<String>,
    val dataChannelSizes: List<Long>,
    val dataGraphSrc: List<String>,
    val dataGraphDst: List<String>,
    val dataGraphMessageSize: List<Long>,
    val periodicSources: List<String>,
    val periods: List<Double>,
    val offsets: List<Double>,
    val upsamples: List<String>,
    val upsampleRepetitiveHolds: List<Long>,
    val upsampleInitialHolds: List<Long>,
    val downsamples: List<String>,
    val downampleRepetitiveSkips: List<Long>,
    val downampleInitialSkips: List<Long>,
    val triggerGraphSrc: List<String>,
    val triggerGraphDst: List<String>,
    val hasORTriggerSemantics: Set<String>
) : DecisionModelWithBody {

    val triggerGraph = triggerGraphSrc.zip(triggerGraphDst)
    override fun getBodyAsText(): String =
        Json.encodeToString(serializer(), this)

    override fun getBodyAsBytes(): ByteArray =
        MsgPack.encodeToByteArray(serializer(), this)

    override fun header(): DecisionModelHeader =
        DecisionModelHeader(
            category = "CommunicatingAndTriggeredReactiveWorkload",
            coveredElements = tasks + upsamples + downsamples + periodicSources + dataChannels,
            coveredRelations = triggerGraph.map { (s, t) -> LabelledArcWithPorts(src = s, dst = t) },
            bodyPath = null
        )

}

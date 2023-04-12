package idesyde.core.headers

import kotlinx.serialization.*
@Serializable
data class LabelledArcWithPorts (
        val dst: String,

        @SerialName("dst_port")
        val dstPort: String? = null,

        val label: String? = null,
        val src: String,

        @SerialName("src_port")
        val srcPort: String? = null
)

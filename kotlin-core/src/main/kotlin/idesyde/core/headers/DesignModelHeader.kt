package idesyde.core.headers

import kotlinx.serialization.*
@Serializable
data class DesignModelHeader (
        val category: String,
        val elements: List<String>,

        @SerialName("model_paths")
        val modelPaths: List<String>,

        val relations: List<LabelledArcWithPorts>
)

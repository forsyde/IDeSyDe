package idesyde.core.headers

import kotlinx.serialization.*
@Serializable
data class DecisionModelHeader (
        @SerialName("body_path")
        val bodyPath: String? = null,

        val category: String,

        @SerialName("covered_elements")
        val coveredElements: List<String>,

        @SerialName("covered_relations")
        val coveredRelations: List<LabelledArcWithPorts>
)

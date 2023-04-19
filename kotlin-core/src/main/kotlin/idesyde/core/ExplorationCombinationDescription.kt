package idesyde.core

import kotlinx.serialization.*

@Serializable
data class ExplorationCombinationDescription(
        @SerialName("can_explore")
        val canExplore: Boolean,

        val criteria: Map<String, Double>
)

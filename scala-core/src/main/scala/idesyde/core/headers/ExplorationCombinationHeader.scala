package idesyde.core.headers

import upickle.default.*

final case class ExplorationCombinationHeader(
    val explorer_header: ExplorerHeader,
    val decision_model_header: DecisionModelHeader,
    val criteria: Map[String, Double] = Map()
) derives ReadWriter {

    def asText: String = write(this)

    def asBinary: Array[Byte] = writeBinary(this)
}

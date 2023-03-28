package idesyde.core.headers

import upickle.default.*

case class DesignModelHeader(
    val category: String,
    val model_paths: Set[String],
    val elements: Set[String],
    val relations: Set[LabelledArcWithPorts]
) derives ReadWriter {

    def asText: String = write(this)

    def asBinary: Array[Byte] = writeBinary(this)
}

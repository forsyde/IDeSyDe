package idesyde.core.headers

import upickle.default.*

case class DesignModelHeader(
    val category: String,
    val elements: Set[String],
    val model_paths: Set[String],
    val relations: Set[LabelledArcWithPorts]
) derives ReadWriter

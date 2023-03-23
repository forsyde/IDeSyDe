package idesyde.core.headers

import upickle.default.*

case class DesignModelHeader (
    val category : String,
    val elements : Seq[String],
    val model_paths : Seq[String],
    val relations : Seq[LabelledArcWithPorts]
) derives ReadWriter

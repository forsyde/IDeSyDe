package idesyde.core.headers

import upickle.default.*

case class DecisionModelHeader (
    val body_paths : Seq[String],
    val category : String,
    val covered_elements : Seq[String],
    val covered_relations : Seq[LabelledArcWithPorts]
) derives ReadWriter 
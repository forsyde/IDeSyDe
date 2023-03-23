package idesyde.core.headers

import upickle.default.*

case class LabelledArcWithPorts (
    val src : String,
    val src_port : Option[String] = None,
    val label : Option[String] = None,
    val dst : String,
    val dst_port : Option[String] = None
) derives ReadWriter

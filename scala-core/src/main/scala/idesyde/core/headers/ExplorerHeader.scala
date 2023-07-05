package idesyde.core.headers

import upickle.default.*

final case class ExplorerHeader(
    val identifier: String,
    val explorer_path: String = ""
) derives ReadWriter

package idesyde.core

import upickle.default._

final case class ExplorerConfiguration(
    max_sols: Long = -1L,
    total_timeout: Long = -1L,
    time_resolution: Long = -1L,
    memory_resolution: Long = -1L,
    strict: Boolean = false
) derives ReadWriter {}

package idesyde.core

import upickle.default._

final case class ExplorerConfiguration(
    max_sols: Long,
    total_timeout: Long,
    time_resolution: Long,
    memory_resolution: Long,
    improvement_iterations: Long,
    improvement_timeout: Long,
    strict: Boolean
) derives ReadWriter {}

object ExplorerConfiguration {
  def fromJsonString(s: String): ExplorerConfiguration =
    read(s)
}

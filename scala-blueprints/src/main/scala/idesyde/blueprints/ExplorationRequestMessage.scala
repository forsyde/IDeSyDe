package idesyde.blueprints

import upickle.default._
import idesyde.core.Explorer
import idesyde.core.ExplorerConfiguration

final case class ExplorationRequestMessage(
    model_message: DecisionModelMessage,
    previous_solutions: Set[ExplorationSolutionMessage],
    configuration: ExplorerConfiguration
) derives ReadWriter

object ExplorationRequestMessage {
  def fromJsonString(s: String): ExplorationRequestMessage = read(s)
}

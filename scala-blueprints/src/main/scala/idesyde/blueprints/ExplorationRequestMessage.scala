package idesyde.blueprints

import upickle.default._
import idesyde.core.Explorer

final case class ExplorationRequestMessage(
    model_message: DecisionModelMessage,
    previous_solutions: Set[ExplorationSolutionMessage],
    configuration: Explorer.Configuration
) derives ReadWriter

object ExplorationRequestMessage {
  def fromJsonString(s: String): ExplorationRequestMessage = read(s)
}

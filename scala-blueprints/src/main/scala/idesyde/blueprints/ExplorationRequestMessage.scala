package idesyde.blueprints

import upickle.default._

final case class ExplorationRequestMessage(
    explorer_id: String,
    model_message: DecisionModelMessage,
    previous_solutions: Set[ExplorationSolutionMessage]
) derives ReadWriter

object ExplorationRequestMessage {
  def fromJsonString(s: String): ExplorationRequestMessage = read(s)
}

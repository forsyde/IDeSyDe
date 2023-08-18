package idesyde.blueprints

import upickle.default._

final case class ExplorationRequestMessage(
    explorer_id: String,
    model_message: DecisionModelMessage,
    objectives: Set[Map[String, Double]]
) derives ReadWriter

object ExplorationRequestMessage {
  def fromJsonString(s: String): ExplorationRequestMessage = read(s)
}

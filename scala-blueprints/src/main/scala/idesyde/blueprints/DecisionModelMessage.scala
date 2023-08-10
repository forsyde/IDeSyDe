package idesyde.blueprints

import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DecisionModel
import idesyde.core.CompleteDecisionModel

import upickle.default._

final case class DecisionModelMessage(
    val header: DecisionModelHeader,
    val body: Option[String]
) {

  def asText: String = write(this)

  def withEscapedNewLinesText: String = write(
    copy(body = body.map(_.replace("\r\n", "\\r\\n").replace("\n", "\\n")))
  )

  def withUnescapedNewLinesText: String = write(
    copy(body = body.map(_.replace("\\r\\n", "\r\n").replace("\\n", "\n")))
  )
}

object DecisionModelMessage {
  def fromDecisionModel(m: DecisionModel): DecisionModelMessage = m match {
    case comp: CompleteDecisionModel =>
      DecisionModelMessage(
        header = comp.header,
        body = Some(comp.bodyAsText.replace("\r\n", "\\r\\n").replace("\n", "\\n"))
      )
    case mm: DecisionModel => DecisionModelMessage(header = mm.header, body = None)
  }

  given ReadWriter[DecisionModelMessage] = upickle.default
    .readwriter[ujson.Value]
    .bimap[DecisionModelMessage](
      x =>
        ujson.Obj(
          "header" -> writeJs(x.header),
          "body"   -> x.body.map(ujson.Str(_)).getOrElse(ujson.Null)
        ),
      json =>
        DecisionModelMessage(
          json.objOpt
            .flatMap(_.get("header").map(read[DecisionModelHeader](_)))
            .get,
          json.objOpt.flatMap(_.get("body").flatMap(_.strOpt))
        )
    )

  def fromJsonString(s: String): DecisionModelMessage = read(s)

}

package idesyde.blueprints

import upickle.default._

import idesyde.core.headers.DesignModelHeader
import idesyde.core.DesignModel

final case class DesignModelMessage(
    val header: DesignModelHeader,
    val body: Option[String]
) {

  def asText: String = write(this)

  def withEscapedNewLinesText: DesignModelMessage =
    copy(body = body.map(_.replace("\r\n", "\\r\\n").replace("\n", "\\n")))

  def withUnescapedNewLinesText: DesignModelMessage =
    copy(body = body.map(_.replace("\\r\\n", "\r\n").replace("\\n", "\n")))
}

object DesignModelMessage {

  given ReadWriter[DesignModelMessage] = upickle.default
    .readwriter[ujson.Value]
    .bimap[DesignModelMessage](
      x =>
        ujson.Obj(
          "header" -> writeJs(x.header),
          "body"   -> x.body.map(ujson.Str(_)).getOrElse(ujson.Null)
        ),
      json =>
        DesignModelMessage(
          json.objOpt
            .flatMap(_.get("header").map(read[DesignModelHeader](_)))
            .get,
          json.objOpt.flatMap(_.get("body").flatMap(_.strOpt))
        )
    )

  def fromJsonString(s: String): DesignModelMessage = read(s)

  def fromDesignModel(m: DesignModel): DesignModelMessage =
    DesignModelMessage(m.header, m.bodyAsText)

}

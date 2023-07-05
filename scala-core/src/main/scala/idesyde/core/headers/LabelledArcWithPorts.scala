package idesyde.core.headers

import upickle.default.*

case class LabelledArcWithPorts(
    val src: String,
    val src_port: Option[String] = None,
    val label: Option[String] = None,
    val dst: String,
    val dst_port: Option[String] = None
) {
  override def equals(x: Any): Boolean = x match {
    case LabelledArcWithPorts(osrc, osrc_port, olabel, odst, odst_port) =>
      src == osrc &&
        dst == odst &&
        ((src_port, osrc_port) match {
          case (Some(a), Some(b)) => true
          case (None, None)       => true
          case _                  => false
        }) &&
        ((dst_port, odst_port) match {
          case (Some(a), Some(b)) => true
          case (None, None)       => true
          case _                  => false
        }) &&
        ((label, olabel) match {
          case (Some(a), Some(b)) => true
          case (None, None)       => true
          case _                  => false
        })
  }
}

object LabelledArcWithPorts {
  def conv(x: LabelledArcWithPorts): ujson.Value = ujson.Obj(
    "src"      -> x.src,
    "src_port" -> x.src_port.map(ujson.Str(_)).getOrElse(ujson.Null),
    "label"    -> x.label.map(ujson.Str(_)).getOrElse(ujson.Null),
    "dst"      -> x.dst,
    "dst_port" -> x.dst_port.map(ujson.Str(_)).getOrElse(ujson.Null)
  )
  def invConv(json: ujson.Value) = LabelledArcWithPorts(
    json.objOpt.flatMap(_.get("src").flatMap(_.strOpt)).get,
    json.objOpt.flatMap(_.get("src_port").flatMap(_.strOpt)),
    json.objOpt.flatMap(_.get("label").flatMap(_.strOpt)),
    json.objOpt.flatMap(_.get("dst").flatMap(_.strOpt)).get,
    json.objOpt.flatMap(_.get("dst_port").flatMap(_.strOpt))
  )
  val rwLabelledArcWithPorts = upickle.default
    .readwriter[ujson.Value]
    .bimap[LabelledArcWithPorts](
      conv,
      invConv
    )
  given ReadWriter[LabelledArcWithPorts] = rwLabelledArcWithPorts
}

package idesyde.core.headers

import upickle.default.*

case class DecisionModelHeader(
    val category: String,
    val body_path: Option[String],
    val covered_elements: Set[String]
    // val covered_relations: Set[LabelledArcWithPorts]
) {

  override def equals(x: Any): Boolean = x match {
    case DecisionModelHeader(ocategory, _, ocovered_elements) =>
      category == ocategory && covered_elements == ocovered_elements // && covered_relations == ocovered_relations
    case _ => false
  }

  override def hashCode(): Int = category.hashCode()

  def dominates(o: DecisionModelHeader): Boolean = category == o.category &&
    o.covered_elements.subsetOf(
      covered_elements
    ) //&& o.covered_relations.subsetOf(covered_relations)

  def asText: String = write(this)

  def asBinary: Array[Byte] = writeBinary(this)

}

object DecisionModelHeader {
  given ReadWriter[DecisionModelHeader] = upickle.default
    .readwriter[ujson.Value]
    .bimap[DecisionModelHeader](
      x =>
        ujson.Obj(
          "category"         -> x.category,
          "body_path"        -> x.body_path.map(ujson.Str(_)).getOrElse(ujson.Null),
          "covered_elements" -> ujson.Arr.from(x.covered_elements)
          // "covered_relations" -> ujson.Arr.from(x.covered_relations)
        ),
      json =>
        // println(json.toString)
        DecisionModelHeader(
          json.objOpt.flatMap(_.get("category").flatMap(_.strOpt)).get,
          json.objOpt.flatMap(_.get("body_path").flatMap(_.strOpt)),
          json.objOpt
            .flatMap(root =>
              root
                .get("covered_elements")
                .flatMap(_.arrOpt)                           // it must be an array
                .map(elems => elems.flatMap(_.strOpt).toSet) // transform all elements to strings
            )
            .getOrElse(Set())
          // json.objOpt
          //   .flatMap(root =>
          //     root
          //       .get("covered_relations")
          //       .flatMap(_.arrOpt) // it must be an array
          //       .map(elems =>
          //         elems.map(LabelledArcWithPorts.invConv).toSet
          //       ) // transform all elements to strings
          //   )
          //   .getOrElse(Set())
        )
    )

  def fromString(s: String): DecisionModelHeader = read[DecisionModelHeader](s)
}

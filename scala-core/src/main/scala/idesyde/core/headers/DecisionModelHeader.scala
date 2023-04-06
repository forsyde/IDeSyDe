package idesyde.core.headers

import upickle.default.*

case class DecisionModelHeader(
    val category: String,
    val body_path: Seq[String],
    val covered_elements: Set[String],
    val covered_relations: Set[LabelledArcWithPorts]
) derives ReadWriter {

  override def equals(x: Any): Boolean = x match {
    case DecisionModelHeader(ocategory, _, ocovered_elements, ocovered_relations) =>
      category == ocategory && covered_elements == ocovered_elements && covered_relations == ocovered_relations
    case _ => false
  }

  def dominates(o: DecisionModelHeader): Boolean = category == o.category &&
    o.covered_elements.subsetOf(covered_elements) && o.covered_relations.subsetOf(covered_relations)

  def asText: String = write(this)

  def asBinary: Array[Byte] = writeBinary(this)

}

package idesyde.core.headers

import upickle.default.*

case class DecisionModelHeader(
    val category: String,
    val body_paths: Set[String],
    val covered_elements: Set[String],
    val covered_relations: Set[LabelledArcWithPorts]
) derives ReadWriter {

  def dominates(o: DecisionModelHeader): Boolean = category == o.category &&
    o.covered_elements.subsetOf(covered_elements) && o.covered_relations.subsetOf(covered_relations)

  def asText: String = write(this)

  def asBinary: Array[Byte] = writeBinary(this)

}

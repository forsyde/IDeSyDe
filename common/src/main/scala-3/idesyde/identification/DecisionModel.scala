package idesyde.identification

trait DecisionModel {

  type VertexT

  def coveredVertexes: Iterable[VertexT]

  def uniqueIdentifier: String

  def dominates(other: DecisionModel): Boolean

  override lazy val hashCode: Int = uniqueIdentifier.hashCode

}

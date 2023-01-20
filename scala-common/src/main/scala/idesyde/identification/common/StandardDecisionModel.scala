package idesyde.identification.common

import idesyde.identification.DecisionModel

/** The StandardDecisionModel is a simple decision model in which all vertexes are simply describe
  * by a string representing their IDs. This means that [[DecisionModel]] s that implement this
  * trait must provide more parameters for their construction.
  *
  * The major advantage of favoring this trait over other [[DecisionModel]] descendants is that it
  * is the most agnostic and universal possible decision model. If, for example, [[this.VertexT]] is
  * of type [[forsyde.io.java.core.Vertex]], then all data classes that implement this trait are
  * dependent on forsyde io.
  *
  * Prefer this trait whenver possible, since it encourages re-usability of design spaces to its
  * maximum.
  */
trait StandardDecisionModel extends DecisionModel {

  type ElementT         = String
  type ElementRelationT = (String, String)

  def elementID(elem: String): String = elem

  def elementRelationID(rel: (String, String)): String = rel.toString()
}

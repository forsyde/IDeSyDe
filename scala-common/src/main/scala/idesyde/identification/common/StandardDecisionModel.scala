package idesyde.identification.common

import idesyde.identification.DecisionModel

/** The StandardDecisionModel is a simple decision model in which all elements are simply described
  * by a string. This means that [[DecisionModel]] s that implement this trait must provide more
  * parameters for their construction.
  *
  * The major advantage of favouring this trait over other [[DecisionModel]] descendants is that it
  * is the most agnostic possible decision model from an implementation perspective. If, for
  * example, the [[ElementT]] is of type `forsyde.io.java.core.Vertex`, then all data classes that
  * implement this trait are dependent on [[https://github.com/forsyde/forsyde-ioForSyDe IO]].
  *
  * Prefer this trait whenever possible, since it encourages re-usability of design spaces to its
  * maximum.
  */
trait StandardDecisionModel extends DecisionModel {

  type ElementT         = String
  type ElementRelationT = (String, String)

  def elementID(elem: String): String = elem

  def elementRelationID(rel: (String, String)): String = rel.toString()
}

package idesyde.identification.common

import idesyde.core.DecisionModel
import idesyde.core.headers.LabelledArcWithPorts

/** The [[StandardDecisionModel]] is a simple decision model in which all elements are simply
  * described by a [[String]].
  *
  * The major advantage of favouring this trait over other [[DecisionModel]] descendants is that it
  * is the most agnostic possible decision model from an implementation perspective. By that, we
  * mean that sharing if the identification procedure is implemented in a multi-tool manner, using
  * [[String]] as the both the element type [[ElementT]] and the ID makes consistency a breeze.
  * Consequently, it also promotes higher decoupling between [[DecisionModel]] s and
  * [[idesyde.identification.DesignModel]] s. If, for example, the [[ElementT]] is of type
  * `forsyde.io.java.core.Vertex`, then all data classes that implement this trait are dependent on
  * [ForSyDe IO](https://github.com/forsyde/forsyde-io).
  *
  * Prefer this trait whenever possible, since it encourages re-usability of design spaces to its
  * maximum.
  */
trait StandardDecisionModel extends DecisionModel {

  type ElementT         = String
  type ElementRelationT = (String, String)

  def elementID(elem: String): String = elem

  def elementRelationID(rel: (String, String)): LabelledArcWithPorts =
    LabelledArcWithPorts(rel._1, None, None, rel._2, None)
}

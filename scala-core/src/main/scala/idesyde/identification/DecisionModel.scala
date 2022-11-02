package idesyde.identification

import idesyde.utils.CoreUtils

/** The trait/interface for a decision model in the design space identification methodology, as
  * defined in [1].
  *
  * A decision model holds information on how to build a design space that is explorable. In other
  * words, an object that implements this trait is assumed to provide parameters, and/or decision
  * variables, and/or analysis techniques for a certain design model.
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait DecisionModel {

  type VertexT

  def uniqueIdentifier: String

  def coveredVertexes: scala.collection.Iterable[VertexT]

  def dominates[D <: DecisionModel](other: D): Boolean = {
    coveredVertexes.exists(vId => other.coveredVertexes.forall(oId => vId != oId))
  }

  override lazy val hashCode: Int = uniqueIdentifier.hashCode

}

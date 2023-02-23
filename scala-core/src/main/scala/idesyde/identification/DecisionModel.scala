package idesyde.identification

/** The trait/interface for a decision model in the design space identification methodology, as
  * defined in [1].
  *
  * A decision model holds information on how to build a design space that is explorable. In other
  * words, an object that implements this trait is assumed to provide parameters, and/or decision
  * variables, and/or analysis techniques for a certain design model. The trait itself is the bare
  * minimum so that the identification procedure can be performed to completion properly.
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait DecisionModel {

  /** This depenent type hides away what type of element this [[DecisionModel]] is abstracting
    */
  type ElementT

  /** This depenent type hides away what type of element relation this [[DecisionModel]] is
    * abstracting
    */
  type ElementRelationT

  /** This in theory should return the name of the class, but might be removed in the future if not
    * necessary or its generation gets automated.
    */
  def uniqueIdentifier: String

  /** This function is of great important because it is used to check whether this [[DecisionModel]]
    * is a good identification coverage of any set of [[idesyde.identification.DesignModel]] or not.
    * In essence, the bigger the coverage for any [[idesyde.identification.DesignModel]], then the
    * more of it is identified and abstracted by this [[DecisionModel]]. This should be given
    * _extreme_ care, since it is the difference between the identification procedure working
    * properly and not.
    *
    * @return
    *   the set of covered elements from the [[idesyde.identification.DesignModel]] s that generated
    *   it.
    */
  def coveredElements: Set[ElementT]

  /** This is essence the same as [[coveredElements]], but for the relations between elements that
    * are been abstracted.
    *
    * @return
    *   the set of covered element relations from the [[idesyde.identification.DesignModel]] s that
    *   generated it.
    * @see
    *   [[coveredElements]]
    */
  def coveredElementRelations: Set[ElementRelationT]

  /** This function compares two [[DecisionModel]] s in order to find out which one identifies
    * [[idesyde.identification.DesignModel]] _better_. In other words, which one has a bigger
    * covering, or if there a full overlap at all. This method is also heavily relied upon during
    * the idenfitication procedure to state whether a fix-point has been reached or not.
    *
    * The default implementation checks whether the [[coveredElements]] and the
    * [[coveredElementRelations]] are a subset between the two [[DecisionModel]] s. This is already
    * good enough for many cases, specially for when then two [[DecisionModel]] s being compared are
    * of different classes, i.e. are actual different [[DecisionModel]] s in terms of parameters.
    *
    * However, there are a couple cases when _additional_ checks can be done to have a finer-grained
    * notion of dominance (emphasis on **additional**): when comparing two [[DecisionModel]] objects
    * of the same type. In this case, it might be possible that one [[DecisionModel]] contains more
    * information than the other, even though their coverage could be possibly the same. Once more,
    * a very well-designed [[DecisionModel]] should _not_ require special treatment for dominance,
    * since all the dominance aspects are ideally computed by subset comparisons. But life has to be
    * taken realistically, and thus the option exists.
    *
    * Thus, the gold rule-of-thumb here is: if you need to override the default implementation, that
    * is okay as long as you think long and hard how could the same dominance comparisons be
    * achieved with just comparison between [[coveredElements]] and [[coveredElementRelations]].
    *
    * @param other
    *   another [[DecisionModel]] to be checked for dominance.
    * @return
    *   whether this [[DecisionModel]] dominates other or not.
    */
  def dominates[D <: DecisionModel](other: D): Boolean = {
    other.coveredElementIDs.subsetOf(coveredElementIDs) &&
    other.coveredElementRelationIDs.subsetOf(coveredElementRelationIDs)
  }

  /** This function connects the inner type [[ElementT]] of this [[DecisionModel]] with potentially
    * any other [[DecisionModel]]. The idea is that the common ground for comparing identifiers is a
    * simple [[String]], since all major languages should support at least one version of it (UTF-8
    * is the default unless otherwise specified) and a human could potentially understand the
    * identifiers of all the elements of a model once they are stringified.
    *
    * @param elem
    *   the input element
    * @return
    *   the string representation of an element in this [[DecisionModel]]
    */
  def elementID(elem: ElementT): String

  /** The same idea as [[elementID]] but for the [[ElementRelationT]] inner type.
    *
    * @param rel
    *   the input relation
    * @return
    *   the string representation of a relation in this [[DecisionModel]]
    */
  def elementRelationID(rel: ElementRelationT): String

  /** This function is a simple shorthand for the stringification of all [[coveredElements]].
    *
    * @return
    *   all stringified [[coveredElements]]
    */
  def coveredElementIDs: Set[String] = coveredElements.map(elementID)

  /** This function is a simple shorthand for the stringification of all
    * [[coveredElementRelations]].
    *
    * @return
    *   all stringified [[coveredElementRelations]]
    */
  def coveredElementRelationIDs: Set[String] = coveredElementRelations.map(elementRelationID)

}

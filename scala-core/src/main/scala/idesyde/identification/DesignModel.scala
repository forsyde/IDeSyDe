package idesyde.identification

/** The trait/interface for a design model in the design space identification methodology, as
  * defined in [1].
  *
  * A design model is the specification .
  *
  * In order to make this class 100% sensible from a Java and OOP perspective, the dominance method
  * takes the base model as an input, instead of it being a type inside the trait. This does not
  * change much from the conceptual perspective, as the extensions of this trait can simply
  * implemented a coverage-based dominance criterium, as originally proposed in [1].
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait DesignModel {

  type VertexT
  type EdgeT

  def merge(other: DesignModel): Option[DesignModel]

  def elements: Iterable[VertexT]

  def elementRelations: Iterable[EdgeT]

  def +(other: DesignModel) = merge(other)
}

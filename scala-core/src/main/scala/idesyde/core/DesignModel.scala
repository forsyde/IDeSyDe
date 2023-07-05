package idesyde.core

import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.LabelledArcWithPorts

/** The trait/interface for a design model in the design space identification methodology, as
  * defined in [1].
  *
  * In essence, the [[DesignModel]] is the system model, or pragmatically, a wrapper around the
  * original system model data types. It can be thought of conceptually as the "database" with all
  * the information about the system we need. The only requirement that is imposed on concrete
  * [[DesignModel]] s is that they have a notion of "identifiers" so that two elements of type
  * [[ElementT]] can always be compared for equality and uniqueness. The ID of an element does not
  * have to be anything pretty, it could very well be integers, as long as they are _unique_ for
  * _unique_ elements.
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  *
  * @see
  *   [[idesyde.identification.StandaloneIdentificationModule]]
  * @see
  *   [[idesyde.identification.IdentificationHandler]]
  */
trait DesignModel {

  type ElementT
  type ElementRelationT

  def merge(other: DesignModel): Option[DesignModel]

  def elements: Set[ElementT]

  // def elementRelations: Set[ElementRelationT]

  def elementID(elem: ElementT): String

  // def elementRelationID(rel: ElementRelationT): String

  def elementIDs: Set[String] = elements.map(elementID)

  // def elementRelationIDs: Set[String] = elementRelations.map(elementRelationID)

  def +(other: DesignModel) = merge(other)

  def category: String

  def header: DesignModelHeader = DesignModelHeader(
    category,
    Set(),
    elementIDs //++ elementRelationIDs
  )

  // override def equals(x: Any): Boolean = x match {
  //   case dm: DesignModel =>
  //     uniqueIdentifier == dm.uniqueIdentifier &&
  //       elementIDs == dm.elementIDs
  //   // && elementRelationIDs == dm.elementRelationIDs
  //   case _ => false
  // }
}

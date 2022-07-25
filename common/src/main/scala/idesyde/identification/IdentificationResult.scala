package idesyde.identification

import java.util.Optional

/** This class exist to ensure compatibility with Java, as the tuples can become awkward in other
  * languages other than scala. It changes nothing in the concepts of the identification process,
  * but garantees that the tooling will remain understandable outside of Scala and in other JVM
  * languages.
  *
  * In summary, this class is equivalent to a tuple (isFixed, identifiedModel), where
  * identifiedModel can be null and isFixed is a boolean.
  */
final case class IdentificationResult[M <: DecisionModel](
    private val _fixed: Boolean = false,
    private val _identified: M = null
) {

  def this(fixed: Boolean, identified: Option[M]) = {
    this(fixed, identified.get)
  }

  def this(identTuple: (Boolean, Option[M])) = {
    this(identTuple._1, identTuple._2.get)
  }

  def identified: Option[M] = if (_identified != null) Some(_identified) else Option.empty

  def getIdentified(): Optional[M] =
    if (_identified != null) Optional.of(_identified) else Optional.empty

  def hasIdentified(): Boolean = _identified != null

  def isFixed(): Boolean = _fixed

}

object IdentificationResult {
  def unapply[M <: DecisionModel](identificationResult: IdentificationResult[M]) =
    (identificationResult.isFixed(), identificationResult.identified)

}

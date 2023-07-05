package idesyde.core

import upickle.default.*

/** A completion for [[DecisionModel]]
  * 
  * This trait refines [[DecisionModel]] in the sense that its body is also completely specified to be serialized and deserialized.
  */
trait CompleteDecisionModel extends DecisionModel {

    def bodyAsText: String

    def bodyAsBinary: Array[Byte]

  
}

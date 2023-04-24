package idesyde.core

interface DecisionModelWithBody : DecisionModel {

    fun getBodyAsText(): String

    fun getBodyAsBytes(): ByteArray
}
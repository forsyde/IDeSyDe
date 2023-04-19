package idesyde.core

import idesyde.core.headers.DecisionModelHeader

interface DecisionModel {

    fun header(): DecisionModelHeader
}
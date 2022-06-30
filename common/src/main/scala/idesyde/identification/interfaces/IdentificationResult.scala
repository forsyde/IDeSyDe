package idesyde.identification.interfaces

enum IdentificationResult[DecisionModel] {
    case FixPoint() extends IdentificationResult[DecisionModel]
}
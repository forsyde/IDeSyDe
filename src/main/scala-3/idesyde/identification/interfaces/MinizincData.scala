package idesyde.identification.interfaces

enum MinizincData:
    case MznLiteral(val literal: Double | Int | String | Boolean) extends MinizincData
    case MznArray(val values: Seq[MinizincData]) extends MinizincData
    case MznSet(val values: Set[MinizincData])
    case MznEnum(val values: Seq[String]) extends MinizincData
package idesyde.matlab

data class SimulinkArc(
    val src: String,
    val dst: String,
    val srcPort: String,
    val dstPort: String,
    val dataSize: Long
)

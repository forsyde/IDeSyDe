package idesyde.identification.choco.models.platform

import org.chocosolver.solver.Model
import idesyde.identification.choco.interfaces.ChocoModelMixin

class TimingPartitioningModule(
    val chocoModel: Model,
    val processSizes: Array[Int],
    val dataSizes: Array[Int],
    val memorySizes: Array[Int]
) extends ChocoModelMixin() {
}

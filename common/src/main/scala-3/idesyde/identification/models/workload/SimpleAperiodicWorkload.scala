package idesyde.identification.models.workload

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.workload.AperiodicWorkload
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.impl.DataBlock

final case class SimpleAperiodicWorkload() extends ForSyDeDecisionModel, AperiodicWorkload[Task, DataBlock] {

}

package idesyde.identification.models.workload

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.workload.AperiodicWorkloadMixin
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.impl.DataBlock

final case class SimpleAperiodicWorkload()
    extends ForSyDeDecisionModel
    with AperiodicWorkloadMixin[Task, DataBlock] {}

package idesyde.exploration.choco.extra.explorers;

import org.chocosolver.solver.Priority;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.events.IntEventType;
import org.chocosolver.util.ESat;

import java.util.Arrays;

class DependentWorkloadFPPropagator extends Propagator<IntVar> {

    int schedulerIdx;
    int[] taskPrios;
    int[] schedulingPoints;
    int[] taskPeriods;
    IntVar[] taskExecutions;
    IntVar[] taskBlocking;
    IntVar[] taskResponses;
    IntVar[] taskDurations;

    public DependentWorkloadFPPropagator(
            int schedulerIdx,
            int[] taskPrios,
            int[] taskPeriods,
            IntVar[] taskExecutions,
            IntVar[] taskBlocking,
            IntVar[] taskResponses,
            IntVar[] taskDurations) {
        super((IntVar[]) ArrayUtils.addAll(taskExecutions, taskResponses, taskBlocking, taskDurations), PropagatorPriority.TERNARY, false);
        this.schedulerIdx = schedulerIdx;
        this.taskPrios = taskPrios;
        this.taskPeriods = taskPeriods;
        this.taskExecutions = taskExecutions;
        this.taskBlocking = taskBlocking;
        this.taskResponses = taskResponses;
        this.taskDurations = taskDurations;
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        if (vIdx < taskExecutions.length) {
            return IntEventType.combine(IntEventType.INSTANTIATE, IntEventType.BOUND, IntEventType.REMOVE);
        } else {
            return IntEventType.combine(IntEventType.BOUND);
        }
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        for (int i = 0; i < taskExecutions.length; i++) {
            final IntVar pi = taskExecutions[i];
            int rt = 0;
            int rtNext = taskBlocking[i].getUB() + taskDurations[i].getUB();
            while (rt != rtNext) {
                rt = rtNext;
                rtNext = taskBlocking[i].getUB() + taskDurations[i].getUB();
                for (int j = 0; j < taskExecutions.length; j++) {
                    final IntVar pj = taskExecutions[j];
                    if (i != j && taskExecutions[i].contains(schedulerIdx) && taskExecutions[j].contains(schedulerIdx) && taskPrios[i] <= taskPrios[j]) {
                        rtNext += taskDurations[j].getUB() * (Math.floorDiv(rt, taskPeriods[j]) + 1);
                    }
                }

            }
            if (rt < taskResponses[i].getUB()) taskResponses[i].updateUpperBound(rt, this);
        }
    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        final int taskIdx = idxVarInProp % taskResponses.length;
        super.propagate(idxVarInProp, mask);
    }

    @Override
    public ESat isEntailed() {
        boolean allInstanteated = true;
        for (IntVar v : taskResponses) {
            allInstanteated = allInstanteated && v.isInstantiated();
        }
        if (allInstanteated) return ESat.TRUE;
        else return ESat.UNDEFINED;
    }
}

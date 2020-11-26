import abc
import importlib.resources as res
from typing import Optional, Set, Tuple

from forsyde.io.python import ForSyDeModel
from minizinc import Model, Solver, Instance

from desyder.identification import DecisionModel, MinizincAble


class Explorer(abc.ABC):

    @abc.abstractmethod
    def can_explore(decision_model: DecisionModel) -> bool:
        return False

    @abc.abstractmethod
    async def explore(decision_model: DecisionModel) -> Optional[ForSyDeModel]:
        return None

    @abc.abstractmethod
    def dominates(
        self,
        other: "Explorer",
        decision_model: DecisionModel
    ) -> (bool, bool):
        '''
        This interface returns domination of one explorer over
        another regarding (completude, speed). If the explorer
        is likely to run faster for the given decision model, then
        it returns (_, True). Likewise, if the explorer _is_ guaranteed
        to guarantee more complete solution(s), it returns (True, _).
        '''
        return (False, False)


class MinizincExplorer(Explorer):

    def can_explore(decision_model):
        return isinstance(decision_model, MinizincAble)

    async def explore(decision_model, backend_solver_name='gecode'):
        mzn_model_name = decision_model.get_mzn_model_name()
        mzn_model = Model(res.read_text('desyder.minizinc', mzn_model_name))
        backend_solver = Solver.lookup(backend_solver_name)
        instance = Instance(mzn_model, backend_solver)
        decision_model.populate_mzn_model(instance)
        return await instance.solve_async()

    def dominates(self, other, decision_model):
        # leave it as a default complete method for now
        return (True, False)


def _get_standard_explorers() -> Set[Explorer]:
    return set(s() for s in Explorer.__subclasses__())


def explore_decision_model(
    model: ForSyDeModel,
    decision_model: Set[DecisionModel],
    explorers: Set[Explorer] = _get_standard_explorers()
) -> Optional[ForSyDeModel]:
    return ForSyDeModel()

import abc
import importlib.resources as res
from typing import Optional

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

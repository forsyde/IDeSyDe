import asyncio
import logging
import importlib.resources as res
from minizinc import Model
from minizinc import Solver
from minizinc import Instance

from idesyde.exploration.interfaces import Explorer

class MinizincExplorer(Explorer):

    @classmethod
    def is_complete(cls):
        return True

    def can_explore(self, decision_model):
        return False

    def explore(self, decision_model, backend_solver_name='gecode'):
        loop = asyncio.get_event_loop()
        return loop.run_until_complete(self.explore_async(decision_model, backend_solver_name))

    async def explore_async(self, decision_model, backend_solver_name='gecode'):
        mzn_model_name = decision_model.get_mzn_model_name()
        mzn_model_str = res.read_text('idesyde.minizinc', mzn_model_name)
        mzn_model = Model()
        mzn_model.add_string(mzn_model_str)
        backend_solver = Solver.lookup(backend_solver_name)
        instance = Instance(backend_solver, mzn_model)
        decision_model.populate_mzn_model(instance)
        result = await instance.solve_async()
        return decision_model.rebuild_forsyde_model(result)

    def dominates(self, other, decision_model):
        # leave it as a default complete method for now
        return [False, True]



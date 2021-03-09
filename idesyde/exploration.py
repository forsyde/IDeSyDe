import abc
import asyncio
import logging
import importlib.resources as res
from enum import Flag, auto
from typing import Optional
from typing import Set
from typing import Tuple
from typing import List

from forsyde.io.python.api import ForSyDeModel
from minizinc import Model
from minizinc import Solver
from minizinc import Instance

from idesyde.identification.interfaces import DecisionModel
from idesyde.identification.interfaces import MinizincableDecisionModel

logging.basicConfig(filename="minizinc-python.log", level=logging.DEBUG)


class ExplorerCriteria(Flag):
    FAST = auto()
    COMPLETE = auto()


class Explorer(abc.ABC):
    '''
    Explorer main interface.

    This class (interface) representes all explorers that can
    be used during a run of the DSE flow (including DSI). Explorers
    can use other explorers and the identification routines in their
    conception and implementations to achieve scalability
    and precision.
    '''

    @abc.abstractclassmethod
    def is_complete(cls) -> bool:
        '''Get full completeness information

        Returns:
            True if the Explorer is complete (exhaustive) for the given
            decision model. False otherwise.
        '''
        return False

    @abc.abstractmethod
    def can_explore(self, decision_model: DecisionModel) -> bool:
        '''Determines if exploration is possible.
        Returns:
            True if 'decision_model' can be explored (solved) by
            this explorer. False otherwise.
        '''
        return False

    @abc.abstractmethod
    async def explore(self, decision_model: DecisionModel) -> Optional[ForSyDeModel]:
        return None

    @abc.abstractmethod
    def dominates(self, other: "Explorer", decision_model: DecisionModel) -> Tuple[int, int]:
        '''Get comparison information regarding efficiency and completude

        Returns:
            A tuple of ints, both ranging from -100 to 100 to indicate the
            level of higher efficiency and more completude from 'self'
            to 'other. Example,

                res = (50, -75)

            indicates that 'other' is 50 "percent" more efficient but
            it is 75 "percent" less complete than 'self'. That is, it would
            be a less accurate but faster choice.
        '''
        return (0, 0)

    def short_name(self) -> str:
        return str(self.__class__.__name__)


class MinizincExplorer(Explorer):

    @classmethod
    def is_complete(cls):
        return True

    def can_explore(self, decision_model):
        return isinstance(decision_model, MinizincableDecisionModel)

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
        return (True, False)


def _get_standard_explorers() -> Set[Explorer]:
    return set(s() for s in Explorer.__subclasses__())


def choose_explorer(decision_models: List[DecisionModel],
                    explorers: Set[Explorer] = _get_standard_explorers(),
                    criteria: ExplorerCriteria = ExplorerCriteria.COMPLETE) -> List[Tuple[Explorer, DecisionModel]]:
    if criteria & ExplorerCriteria.COMPLETE:
        dominant = [(e, m) for e in explorers for m in decision_models if e.can_explore(m)]
        length = len(dominant)
        length_before = None
        while length != length_before:
            length_before = length
            dominant = [
                (e, m) for (e, m) in dominant
                # keep only the (e,m) that are not dominates by anyone else for m.
                # [0] comes from the fact that we look only at completude
                if not any(o.dominates(e, m)[0] for (o, om) in dominant if m == om and o != e)
            ]
            length = len(dominant)
        return dominant
    return []

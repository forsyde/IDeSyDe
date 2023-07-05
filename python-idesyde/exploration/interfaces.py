import abc
import asyncio
import logging
import importlib.resources as res
from enum import Flag, auto
from typing import Optional
from typing import List

from forsyde.io.python.api import ForSyDeModel

from idesyde.identification.interfaces import DecisionModel


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

    @abc.abstractmethod
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
    def explore(self, decision_model: DecisionModel) -> Optional[ForSyDeModel]:
        return None

    @abc.abstractmethod
    def dominates(self, other: "Explorer", decision_model: DecisionModel) -> List[bool]:
        '''Get comparison information regarding efficiency and completude

        Returns:
            A list of booleans, indicating if it dominates the other explorer,
            for the given decision model, for every criteria avaialble.
            Example,

                res = [True, False, True]

            indicates that 'self' dominates 'other' in criterias #1 and #3, but is
            dominates in #2. The criteria is currently made to match 'ExplorerCriteria'.
        '''
        return [False, False]

    def short_name(self) -> str:
        return str(self.__class__.__name__)



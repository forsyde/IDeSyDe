import logging
import importlib
from enum import Flag, auto
from typing import Tuple
from typing import List

from idesyde.identification.interfaces import DecisionModel
from idesyde.exploration.interfaces import Explorer
from idesyde.exploration.interfaces import ExplorerCriteria

logging.basicConfig(filename="minizinc-python.log", level=logging.DEBUG)


_registered_explorers: List[Explorer] = list()


def _get_standard_explorers(include_internal: bool = True) -> List[Explorer]:
    if include_internal:
        importlib.import_module("idesyde.exploration.minizinc")
    return _registered_explorers


def register_explorator(explorer: Explorer):
    """Decorator to register a explorer to be used in the identification and exploration procedure

    Arguments:
        explorer: must be a class that implements 'Explorer'.
    """
    _registered_explorers.append(explorer)
    return explorer


def choose_explorer(decision_models: List[DecisionModel],
                    explorers: List[Explorer] = _get_standard_explorers(),
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

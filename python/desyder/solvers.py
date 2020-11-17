import abc
from typing import Optional

from forsyde.io.python import ForSyDeModel

from desyder.identification import DecisionProblem, MinizincAble


class Solver(abc.ABC):

    @abc.abstractmethod
    def can_solve(problem: DecisionProblem) -> bool:
        return False

    @abc.abstractmethod
    def solve(problem: DecisionProblem) -> Optional[ForSyDeModel]:
        return None


class MinizincSolver(Solver):

    def can_solve(problem):
        return isinstance(problem, MinizincAble)

    def solve(problem):
        return None

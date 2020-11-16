from typing import List, Set, Optional

from forsyde.io.python import ForSyDeModel

from desyder.identification import DecisionProblem
from desyder.solvers import Solver


class DeSyDeR(object):

    def __init__(self):
        self.standard_problems = set(
            c() for c in DecisionProblem.__subclasses__()
        )
        self.standard_solvers = set(
            s() for s in Solver.__subclasses__()
        )

    def identify_problems(
        self,
        model: ForSyDeModel,
        problems: Optional[Set[DecisionProblem]] = None
    ) -> List[DecisionProblem]:
        if problems is None:
            problems = self.standard_problems
        candidates = set(p for p in problems if not p.at_fix_point)
        identified = set(p for p in problems if p.is_identified)
        while len(candidates) > 0:
            identified = set(
                p for p in problems if p.identify(model, problems)
            )
            candidates = set(
                p for p in problems if not p.at_fix_point
            )
        return identified

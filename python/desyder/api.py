from typing import List, Set, Optional, Type

from forsyde.io.python import ForSyDeModel

from desyder.identification import DecisionProblem
from desyder.solvers import Solver


class DeSyDeR(object):

    def __init__(self):
        self.standard_problems = set(
            c for c in DecisionProblem.__subclasses__()
        )
        self.standard_solvers = set(
            s() for s in Solver.__subclasses__()
        )

    def identify_problems(
        self,
        model: ForSyDeModel,
        problems: Set[Type[DecisionProblem]] = set(),
        solvers: Set[Type[Solver]] = set()
    ) -> List[DecisionProblem]:
        if not problems:
            problems = self.standard_problems
        if not solvers:
            solvers = self.standard_solvers
        max_iterations = len(model) + len(problems)
        candidates = [p for p in problems]
        identified = []
        iterations = 0
        while len(candidates) > 0 and iterations < max_iterations:
            # generate all trials and keep track of which subproblem
            # made the trial
            for c in candidates:
                (fixed, subprob) = c.identify(model, identified)
                # join with the identified
                if subprob:
                    identified.append(subprob)
                # take away candidates at fixpoint
                if fixed:
                    candidates.remove(c)
            iterations += 1
        return identified

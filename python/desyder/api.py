import concurrent.futures
import os
from typing import List, Set, Optional, Type

from forsyde.io.python import ForSyDeModel

from desyder.identification import DecisionModel
from desyder.exploration import Explorer


class DeSyDeR(object):
    '''
    This class represents the program API that can be consumed from
    other python programs and possibly from other languages.

    This also means that all DeSyDeR major logic should be put inside
    this class or future adjoining ones, and not conceptually leaked
    into the CLI module.

    When the class is instantiated, it uses python's built-in features
    to get all subclasses of the provided interfaces for DecisionModel
    and Explorers, when the user does not provide any additional information
    about it.
    '''

    def __init__(self):
        self.standard_problems = set(
            c for c in DecisionModel.__subclasses__()
        )
        self.standard_explorers = set(
            s() for s in Explorer.__subclasses__()
        )

    async def identify_problems(
        self,
        model: ForSyDeModel,
        problems: Set[Type[DecisionModel]] = set(),
        concurrent_idents: int = os.cpu_count() or 1
    ) -> List[DecisionModel]:
        '''
        This function runs the Design Space Identification scheme,
        as presented in paper [DSI-DATE'2021], so that problems can
        be automatically solved from the given input model.

        If the arguments **problems** and **explorers* are not passed,
        the API uses all subclasses found during runtime that implement
        the interfaces DecisionModel and Explorer.
        '''
        problems = self.standard_problems if not problems else problems
        max_iterations = len(model) + len(problems)
        candidates = [p for p in problems]
        identified: List[DecisionModel] = []
        iterations = 0
        with concurrent.futures.ProcessPoolExecutor(
                max_workers=concurrent_idents) as executor:
            while len(candidates) > 0 and iterations < max_iterations:
                # generate all trials and keep track of which subproblem
                # made the trial
                futures = {
                    c: executor.submit(c.identify, model, identified)
                    for c in candidates
                }
                concurrent.futures.wait(futures.values())
                for c in futures:
                    (fixed, subprob) = futures[c].result()
                    # join with the identified
                    if subprob:
                        identified.append(subprob)
                    # take away candidates at fixpoint
                    if fixed:
                        candidates.remove(c)
                iterations += 1
            return identified

    async def explore_problems(
        self,
        model: ForSyDeModel,
        problems: Set[DecisionModel],
        explorers: Set[Explorer] = set()
    ) -> Optional[ForSyDeModel]:
        explorers = self.standard_explorers if not explorers else explorers
        return ForSyDeModel()

import concurrent.futures
import os
import importlib
from enum import Flag
from enum import auto
from typing import List
from typing import Callable
from typing import Tuple
from typing import Optional

# import idesyde.identification.rules as ident_rules
from forsyde.io.python.api import ForSyDeModel
from idesyde.identification.interfaces import DecisionModel
#from idesyde.identification.interfaces import IdentificationRule

IdentificationRuleType = Callable[[ForSyDeModel, List[DecisionModel]], Tuple[bool, Optional[DecisionModel]]]

_registered_rules: List[IdentificationRuleType] = list()


class ChoiceCriteria(Flag):
    '''Flag to indicate decision model choice

    If many models are identified at once, and they are
    super identifications of each other, a choice between them
    may be necessary. This flag represents some possibilities
    for such choice
    '''
    DOMINANCE = auto()


def _get_registered_rules(include_internal: bool = True) -> List[IdentificationRuleType]:
    # read standard modules for rules
    # TODO: maybe there's a future proof way of doing this...
    if include_internal:
        importlib.import_module('idesyde.identification.rules')
    return _registered_rules  # if _registered_rules else list(r_class() for r_class in ident_rules._standard_rules_classes)


def register_identification_rule(func: IdentificationRuleType):
    '''Decorator to register a rule to be used in the identification procedure
    
    Arguments:
        func: must be a function of signature [ForSyDeModel, List[DecisionModel]] -> (bool, Optional[DecisionModel]).
            The first boolean indicates a fixpoint for the rule and the second result is the partially identified
            Decision model, if any.
    '''
    _registered_rules.append(func)
    return func


def identify_decision_models(
    model: ForSyDeModel, rules: List[IdentificationRuleType] = _get_registered_rules()) -> List[DecisionModel]:
    '''
    This function runs the Design Space Identification scheme,
    as presented in paper [DSI-DATE'2021], so that problems can
    be automatically solved from the given input model.

    If the argument **problems** is not passed,
    the API uses all subclasses found during runtime that implement
    the interfaces DecisionModel and Explorer.
    '''
    max_iterations = len(model) * len(rules)
    allowed_rules = [rule for rule in rules]
    identified: List[DecisionModel] = []
    iterations = 0
    while len(allowed_rules) > 0 and iterations < max_iterations:
        trials = ((rule, rule(model, identified)) for rule in allowed_rules)
        for (rule, (fixed, subprob)) in trials:
            # join with the identified
            if subprob:
                identified.append(subprob)
            # take away candidates at fixpoint
            if fixed:
                allowed_rules.remove(rule)
        iterations += 1
    return identified


def identify_decision_models_parallel(model: ForSyDeModel,
                                      rules: List[IdentificationRuleType] = _get_registered_rules(),
                                      concurrent_idents: int = os.cpu_count() or 1) -> List[DecisionModel]:
    '''
    This function runs the Design Space Identification scheme,
    as presented in paper [DSI-DATE'2021], so that problems can
    be automatically solved from the given input model. It also
    uses parallelism to run as many identifications as possible
    simultaneously.

    If the argument **problems** is not passed,
    the API uses all subclasses found during runtime that implement
    the interfaces DecisionModel and Explorer.
    '''
    max_iterations = len(model) * len(rules)
    allowed_rules = [rule for rule in rules]
    identified: List[DecisionModel] = []
    iterations = 0
    with concurrent.futures.ProcessPoolExecutor(max_workers=concurrent_idents) as executor:
        while len(allowed_rules) > 0 and iterations < max_iterations:
            # generate all trials and keep track of which subproblem
            # made the trial
            futures = {rule: executor.submit(rule, model, identified) for rule in allowed_rules}
            concurrent.futures.wait(futures.values())
            for rule in futures:
                (fixed, subprob) = futures[rule].result()
                # join with the identified
                if subprob:
                    identified.append(subprob)
                # take away candidates at fixpoint
                if fixed:
                    allowed_rules.remove(rule)
            iterations += 1
        return identified


def choose_decision_models(models: List[DecisionModel],
                           criteria: ChoiceCriteria = ChoiceCriteria.DOMINANCE,
                           desired_names: List[str] = []) -> List[DecisionModel]:
    '''Filter out decision models based on some criteria

    This function enables super identifications to subsume
    sub identifications in the end, otherwise for the final
    exploration step there may be many DecisionModels that
    are not in fact representative of the original ForSyDe IO
    model.

    Returns:
        A filtered list of DecisionModels based on the criteria
        provided _and_ on specific names for some DecisionModels.
        The last option is particularly interesting for debugging
        and advanced solution techniques.
    '''
    if desired_names:
        models = [m for m in models if m.short_name() in desired_names]
    if criteria & ChoiceCriteria.DOMINANCE:
        non_dominated = [m for m in models]
        for m in models:
            for other in models:
                if m in non_dominated and m != other and other.dominates(m):
                    non_dominated.remove(m)
        return non_dominated
    else:
        return models


async def identify_decision_models_async(
    model: ForSyDeModel, rules: List[IdentificationRuleType] = _get_registered_rules()) -> List[DecisionModel]:
    '''
    AsyncIO version of the same function. Wraps the non-async version.
    '''
    return identify_decision_models(model, rules)


async def identify_decision_models_parallel_async(model: ForSyDeModel,
                                                  rules: List[IdentificationRuleType] = _get_registered_rules(),
                                                  concurrent_idents: int = os.cpu_count() or 1) -> List[DecisionModel]:
    '''
    AsyncIO version of the same function. Wraps the non-async version.
    '''
    return identify_decision_models_parallel(model, rules, concurrent_idents)

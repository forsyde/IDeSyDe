from dataclasses import dataclass, asdict, is_dataclass
from json import dumps
from cbor2 import dumps
from typing import Dict, Optional, List, Set


@dataclass
class ExplorationCombinationDescription:
    can_explore: bool = False
    criteria: Dict[str, float] = dict()


@dataclass
class DecisionModelHeader:
    category: str
    covered_elements: Set[str] = set()
    body_path: Optional[str] = None

    def dominates(self, other: "DecisionModelHeader") -> bool:
        return self.category == other.category and all(
            elem in self.covered_elements for elem in other.covered_elements
        )


@dataclass
class DesignModelHeader:
    category: str
    elements: Set[str] = set()
    model_paths: List[str] = list()


class DecisionModel:
    """
    The trait/interface for a decision model in the design space identification methodology, as
    defined in [1].

    A decision model is a collection of parameters and associated functions that potentially define design spaces,
    e.g. a decision model for SDFs with a topology matrix parameter and an associated function to check the existence of deadlocks.

    The header is a necessary abstraction to ensure that the identification procedure terminates properly.
    It also gives and idea on how much of the input models are being "covered" by the decision model in question.

    [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
    Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
    Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
    """

    def unique_identifier(self) -> str:
        return self.__class__.__name__

    def header(self) -> DecisionModelHeader:
        return DecisionModelHeader(category=self.unique_identifier())

    def dominates(self, other: "DecisionModel") -> bool:
        return self.header() > other.header()

    def body_as_json(self) -> Optional[str]:
        if is_dataclass(self):
            return dumps(asdict(self))
        else:
            return None

    def body_as_cbor(self) -> Optional[bytes]:
        if is_dataclass(self):
            return dumps(asdict(self))
        else:
            return None


class DesignModel:
    """
    The trait/interface for a design model in the design space identification methodology, as
    defined in [1].
    A design model is a model used by MDE frameworks and tools, e.g. Simulink and ForSyDe IO.
    Like [DesignModel], this trait requires a header so that the identification procedure can work
    correctly and terminate. The header gives an idea to the framework on how much can be "identified"
    from the input MDE model, i.e. the [DesignModel].

    [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
    Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
    Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
    """

    def unique_identifier(self) -> str:
        return self.__class__.__name__

    def header(self) -> DesignModelHeader:
        return DesignModelHeader(category=self.unique_identifier())

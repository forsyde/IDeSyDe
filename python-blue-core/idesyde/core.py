from dataclasses import dataclass, asdict, is_dataclass, field
import json
import os
from typing import Dict, Optional, List, Set, Iterable, Callable

import cbor2


@dataclass
class ExplorationBid:
    explorer: str
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

    def write_to_path(self, base_path: str, preffix: str = "", suffix: str = ""):
        with open(
            base_path
            + os.path.sep
            + "header_"
            + preffix
            + "_"
            + self.category
            + "_"
            + suffix
            + ".json",
            "w",
        ) as jsonf:
            json.dump(asdict(self), jsonf)
        with open(
            base_path
            + os.path.sep
            + "header_"
            + preffix
            + "_"
            + self.category
            + "_"
            + suffix
            + ".cbor",
            "w",
        ) as cborf:
            cbor2.dump(asdict(self), cborf)


@dataclass
class DesignModelHeader:
    category: str
    elements: Set[str] = set()
    model_paths: List[str] = list()

    def write_to_path(self, base_path: str, preffix: str = "", suffix: str = ""):
        with open(
            base_path
            + os.path.sep
            + "header_"
            + preffix
            + "_"
            + self.category
            + "_"
            + suffix
            + ".json",
            "w",
        ) as jsonf:
            json.dump(asdict(self), jsonf)
        with open(
            base_path
            + os.path.sep
            + "header_"
            + preffix
            + "_"
            + self.category
            + "_"
            + suffix
            + ".cbor",
            "w",
        ) as cborf:
            cbor2.dump(asdict(self), cborf)


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
        return self.header().dominates(other.header())

    def body_as_json(self) -> Optional[str]:
        if is_dataclass(self):
            return json.dumps(asdict(self))
        else:
            return None

    def body_as_cbor(self) -> Optional[bytes]:
        if is_dataclass(self):
            return cbor2.dumps(asdict(self))
        else:
            return None

    def write_to_path(
        self, base_path: str, preffix: str = "", suffix: str = ""
    ) -> DecisionModelHeader:
        header = self.header()
        json_body = self.body_as_json()
        if json_body:
            with open(
                base_path
                + os.path.sep
                + "body_"
                + preffix
                + "_"
                + header.category
                + "_"
                + suffix
                + ".json",
                "w",
            ) as jsonf:
                jsonf.write(json_body)
                header.body_path = (
                    base_path
                    + os.path.sep
                    + "body_"
                    + preffix
                    + "_"
                    + header.category
                    + "_"
                    + suffix
                    + ".json"
                )
        cbor_body = self.body_as_cbor()
        if cbor_body:
            with open(
                base_path
                + os.path.sep
                + "body_"
                + preffix
                + "_"
                + header.category
                + "_"
                + suffix
                + ".cbor",
                "wb",
            ) as cborf:
                cborf.write(cbor_body)
                header.body_path = (
                    base_path
                    + os.path.sep
                    + "body_"
                    + preffix
                    + "_"
                    + header.category
                    + "_"
                    + suffix
                    + ".cbor"
                )
        header.write_to_path(base_path, preffix, suffix)
        return header


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


IdentificationRuleT = Callable[
    [Set[DesignModel], Set[DecisionModel]], Set[DecisionModel]
]
ReverseIdentificationRuleT = Callable[
    [Set[DecisionModel], Set[DesignModel]], Set[DesignModel]
]


class IdentificationModule:
    def unique_identifier(self) -> str:
        return self.__class__.__name__

    def identification_step(
        self,
        iteration: int,
        design_models: Set[DesignModel],
        decision_models: Set[DecisionModel],
    ) -> Set[DecisionModel]:
        return set()

    def reverse_identification(
        self,
        decision_models: Set[DecisionModel],
        design_models: Set[DesignModel],
    ) -> Set[DesignModel]:
        return set()


class Explorer:
    def unique_identifier(self) -> str:
        return self.__class__.__name__

    def bid(self, m: DecisionModel) -> ExplorationBid:
        return ExplorationBid(explorer=self.unique_identifier())

    async def explore(
        self,
        m: DecisionModel,
        max_sols: int,
        total_timeout: int,
        time_resolution: int,
        memory_resolution: int,
    ) -> Iterable[DecisionModel]:
        return []


@dataclass
class ExplorationModule:
    unique_identifier: str
    explorers: List[Explorer] = field(defaul_factory=list)

    def bid(self, m: DecisionModel) -> List[ExplorationBid]:
        return [explorer.bid(m) for explorer in self.explorers]

    async def explore(
        self,
        m: DecisionModel,
        explorer_index: int = 0,
        max_sols: int = 0,
        total_timeout: int = 0,
        time_resolution: int = 0,
        memory_resolution: int = 0,
    ) -> Iterable[DecisionModel]:
        yield self.explorers[explorer_index].explore(
            m, max_sols, total_timeout, time_resolution, memory_resolution
        )

from typing import Optional, Set, Callable, Type, List
from dataclasses import dataclass, field

import cbor2
import json

from idesyde.core import DecisionModel, DesignModel
from idesyde.core import DecisionModel, DesignModel

from idesyde.core import (
    ExplorationModule,
    IdentificationModule,
    DesignModel,
    DecisionModel,
    DecisionModelHeader,
    IdentificationRuleT,
    ReverseIdentificationRuleT,
)


@dataclass
class StandaloneIdentificationModule(IdentificationModule):
    unique_identifier: str
    identification_rules: Set[IdentificationRuleT] = field(default_factory=set)
    decision_models_classes: Set[Type[DecisionModel]] = field(default_factory=set)
    reverse_identification_rules: Set[ReverseIdentificationRuleT] = field(
        default_factory=set
    )
    read_design_model_funcs: List[Callable[[str], Optional[DesignModel]]] = field(
        default_factory=list
    )
    write_design_model_funcs: List[
        Callable[[Optional[DesignModel], str], bool]
    ] = field(default_factory=list)
    decision_model_schemas: Set[str] = field(default_factory=set)

    def read_design_model(self, path: str) -> Optional[DesignModel]:
        for rf in self.read_design_model_funcs:
            opt = rf(path)
            if opt:
                return opt
        return None

    def write_design_model(self, design_model: DesignModel, dest: str) -> bool:
        for rf in self.write_design_model_funcs:
            if rf(design_model, dest):
                return True
        return False

    def decision_header_to_model(
        self,
        header: DecisionModelHeader,
    ) -> Optional[DecisionModel]:
        if header.body_path:
            for decision_cls in self.decision_models_classes:
                if decision_cls.__name__ == header.category:
                    d = dict()
                    if header.body_path.endswith(".cbor"):
                        with open(header.body_path, "rb") as cborf:
                            d = cbor2.load(cborf)
                    elif header.body_path.endswith(".json"):
                        with open(header.body_path, "r") as jsonf:
                            d = json.load(jsonf)
                    return decision_cls(**d)
        return None

    def identification_step(
        self,
        iteration: int,
        design_models: Set[DesignModel],
        decision_models: Set[DecisionModel],
    ) -> Set[DecisionModel]:
        identified = set()
        for irule in self.identification_rules:
            for m in irule(design_models, decision_models):
                if m not in identified:
                    identified.add(m)
        return identified

    def reverse_identification(
        self, decision_models: Set[DecisionModel], design_models: Set[DesignModel]
    ) -> Set[DesignModel]:
        reverse_identified = set()
        for rirule in self.reverse_identification_rules:
            for m in rirule(decision_models, design_models):
                if m not in reverse_identified:
                    reverse_identified.add(m)
        return reverse_identified


@dataclass
class StandaloneExplorationModule(ExplorationModule):
    unique_identifier: str
    decision_models_classes: Set[Type[DecisionModel]] = field(default_factory=set)
    decision_model_schemas: Set[str] = field(default_factory=set)

    def decision_header_to_model(
        self,
        header: DecisionModelHeader,
    ) -> Optional[DecisionModel]:
        if header.body_path:
            for decision_cls in self.decision_models_classes:
                if decision_cls.__name__ == header.category:
                    d = dict()
                    if header.body_path.endswith(".cbor"):
                        with open(header.body_path, "rb") as cborf:
                            d = cbor2.load(cborf)
                    elif header.body_path.endswith(".json"):
                        with open(header.body_path, "r") as jsonf:
                            d = json.load(jsonf)
                    return decision_cls(**d)
        return None

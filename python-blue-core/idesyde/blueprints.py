import argparse
import os
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
        Callable[[Optional[DesignModel], str], Optional[str]]
    ] = field(default_factory=list)
    decision_model_schemas: Set[str] = field(default_factory=set)

    def read_design_model(self, path: str) -> Optional[DesignModel]:
        for rf in self.read_design_model_funcs:
            opt = rf(path)
            if opt:
                return opt
        return None

    def write_design_model(self, design_model: DesignModel, dest: str) -> List[str]:
        written = []
        for rf in self.write_design_model_funcs:
            p = rf(design_model, dest)
            if p:
                written.append(p)
        return written

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


def stadalone_identification_module_main(module: StandaloneIdentificationModule) -> int:
    parser = argparse.ArgumentParser(
        description="IdentificationModule " + module.unique_identifier
    )
    parser.add_argument(
        "-m,--design-path",
        type=str,
        help="The path where the design models (and headers) are stored.",
    )
    parser.add_argument(
        "-i,--identified-path",
        type=str,
        help="The path where identified decision models (and headers) are stored.",
    )
    parser.add_argument(
        "-s,--solved-path",
        type=str,
        help="The path where explored decision models (and headers) are stored.",
    )
    parser.add_argument(
        "-r,--reverse-path",
        type=str,
        help="The path where integrated design models (and headers) are stored.",
    )
    parser.add_argument(
        "-o,--output-path",
        type=str,
        help="The path where final integrated design models are stored, in their original format.",
    )
    parser.add_argument(
        "-t,--identification-step",
        type=int,
        help="The overall identification iteration number.",
    )
    parser.add_argument("--schemas", action="store_true")

    args = parser.parse_args()
    if args.schemmas:
        for s in module.decision_model_schemas:
            print(s)
    else:
        if args.design_path:
            os.makedirs(args.design_path)
            design_models = set()
            for f in os.listdir(args.design_path):
                m = module.read_design_model(f)
                if m:
                    design_models.add(m)
                    h = m.header()
                    h.model_paths.append(f)
                    h.write_to_path(args.design_path, "", module.unique_identifier)
            if args.solved_path and args.reverse_path:
                os.makedirs(args.solved_path)
                os.makedirs(args.reverse_path)
                solved_headers = {
                    ff for ff in os.listdir(args.solved_path) if ff.startswith("header")
                }
                solved_with_none = {
                    DecisionModelHeader.load_from_path(f) for f in solved_headers
                }
                solved = {m for m in solved_with_none if m != None}
                reverse_identified = module.reverse_identification(
                    solved, design_models
                )
                for m in reverse_identified:
                    for rpath in module.write_design_model(m, args.reverse_path):
                        mheader = m.header()
                        mheader.model_paths.append(rpath)
                        mheader.write_to_path(
                            args.reverse_path, "", module.unique_identifier
                        )
                    if args.output_path:
                        module.write_design_model(m, args.output_path)
            elif args.identified_path and args.identification_step:
                os.makedirs(args.identified_path)
                decision_model_headers = {
                    ff
                    for ff in os.listdir(args.identified_path)
                    if ff.startswith("header")
                }
                decision_models_with_none = {
                    DecisionModelHeader.load_from_path(f)
                    for f in decision_model_headers
                }
                decision_models = {m for m in decision_models_with_none if m != None}
                identified = module.identification_step(
                    args.identification_step, design_models, decision_models
                )
                for m in identified:
                    m.write_to_path(
                        args.identified_path,
                        "{0:16d}".format(args.identification_step),
                        module.unique_identifier,
                    )
    return 0


def standalone_exploration_module_main(module: StandaloneExplorationModule) -> int:
    parser = argparse.ArgumentParser(
        description="ExplorationModule " + module.unique_identifier
    )
    parser.add_argument(
        "-i,--dominant-path",
        type=str,
        help="The path where dominant identified decision models (and headers) are stored.",
    )
    parser.add_argument(
        "-o,--solution-path",
        type=str,
        help="The path where explored decision models (and headers) are stored.",
    )
    parser.add_argument(
        "-c,--combine",
        type=str,
        help="The path to a decision model header to make a bidding.",
    )
    parser.add_argument(
        "-e,--explore",
        type=str,
        help="The path to a decision model header to be explored.",
    )
    parser.add_argument(
        "-n,--explorer-idx",
        type=int,
        help="The index of the explorer inside the module to be used.",
    )
    parser.add_argument("--total-timeout", type=int, default=0)
    parser.add_argument("--maximum-solutions", type=int, default=0)
    parser.add_argument("--time-resolution", type=int, default=0)
    parser.add_argument("--memory-resolution", type=int, default=0)
    if args.dominant_path and args.solution_path and args.explore:
        header = DecisionModelHeader.load_from_path(args.explore)
        m = module.decision_header_to_model(header)
        if args.explorer_idx:
            module.explore(
                m,
                args.explorer_idx,
                args.maximum_solutions,
                args.total_timeout,
                args.time_resolution,
                args.memory_resolution,
            )
        else:
            pass
        pass
    elif args.dominant_path and args.combine:
        header = DecisionModelHeader.load_from_path(args.combine)
        m = module.decision_header_to_model(header)
        if m:
            for comb in module.bid(m):
                println(comb.to_json())
    return 0

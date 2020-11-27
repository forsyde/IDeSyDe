'''
The identification module provides all types/classes and methods
to perform DSI as specified in the papers that follows:

    [DSI-DATE2022]

It should _not_ depend in the exploration module, pragmatically
or conceptually, but only on other modules that provides model
utilities, like SDF analysis or SY analysis.
'''
import abc
import concurrent.futures
import importlib.resources as resources
import os
from dataclasses import dataclass, field
from enum import Flag, auto
from typing import List, Union, Tuple, Set, Optional, Dict, Type, Iterable

import numpy as np
import sympy
from minizinc import Model as MznModel
from minizinc import Instance as MznInstance
from minizinc import Result as MznResult
from forsyde.io.python import ForSyDeModel
from forsyde.io.python import Vertex
from forsyde.io.python import Edge
from forsyde.io.python import Port
from forsyde.io.python.types import TypesFactory

import desyder.math as mathutil
import desyder.sdf as sdfapi


class ChoiceCriteria(Flag):
    DOMINANCE = auto()


class MinizincAble(abc.ABC):

    @abc.abstractmethod
    def populate_mzn_model(
            self,
            model: Union[MznModel, MznInstance]
    ) -> Union[MznModel, MznInstance]:
        return model

    @abc.abstractmethod
    def get_mzn_model_name(self) -> str:
        return ""

    @abc.abstractmethod
    def rebuild_forsyde_model(
        self,
        result: MznResult
    ) -> ForSyDeModel:
        return ForSyDeModel()

    def build_mzn_model(self, mzn=MznModel()):
        model_txt = resources.read_text(
            'desyder.minizinc',
            self.get_mzn_model_name()
        )
        mzn.add_string(model_txt)
        self.populate_mzn_model(mzn)
        return mzn


class DecisionModel(abc.ABC):

    """
    Docstring for DecisionModel.

    A dict like interface is implemented for decision models
    for convenience, which recursively checks any other partial
    identifications that the model may have.
    """

    @abc.abstractclassmethod
    def identify(
            cls,
            model: ForSyDeModel,
            subproblems: List["DecisionModel"]
    ) -> Tuple[bool, Optional["DecisionModel"]]:
        """TODO: Docstring for identify.

        :db: TODO
        :returns: TODO

        """
        return (True, None)

    def __iter__(self):
        for k in self.__dict__:
            o = self.__dict__[k]
            if isinstance(o, DecisionModel):
                yield from o
            else:
                yield k

    def __getitem__(self, key):
        key = str(key)
        if key in self.__dict__:
            return self.__dict__[key]
        for k in self.__dict__:
            o = self.__dict__[k]
            if isinstance(o, DecisionModel) and key in o:
                return o[key]
        return KeyError

    def __contains__(self, key):
        key = str(key)
        if key in self.__dict__:
            return True
        for k in self.__dict__:
            o = self.__dict__[k]
            if isinstance(o, DecisionModel) and key in o:
                return True
        return False

    def covered_vertexes(self) -> Iterable[Vertex]:
        for o in self:
            if isinstance(o, Vertex):
                yield o

    def covered_edges(self) -> Iterable[Edge]:
        for o in self:
            if isinstance(o, Edge):
                yield o

    def covered_model(self) -> ForSyDeModel:
        model = ForSyDeModel()
        for v in self.covered_vertexes():
            model.add_node(v, label=v.identifier)
        for e in self.covered_edges():
            model.add_edge(
                e.source_vertex,
                e.target_vertex,
                data=e
            )
        return model

    def dominates(self, other: "DecisionModel") -> bool:
        '''
        This function returns if one partial identification dominates
        the other.
        '''
        # other - self
        other_diff = set(
            k for k in other if k not in self
        )
        # self - other
        self_diff = set(
            k for k in self if k not in other
        )
        # other is fully contained in self and itersection is consistent
        return len(other_diff) == 0\
            and len(self_diff) >= 0
            # and all(self[k] == other[k] for k in other if k in self)


@dataclass
class SDFExecution(DecisionModel):

    """
    This decision model captures all SDF actors and channels in
    the design model and can only be identified if the 'Global' SDF
    application (the union of all disjoint SDFs) is consistent, i.e.
    it has a PASS.

    After identification this decision model provides the global
    SDF topology and the PASS with all elements included.
    """

    sdf_actors: List[Vertex] = field(default_factory=lambda: [])
    sdf_channels: List[Vertex] = field(default_factory=lambda: [])
    sdf_topology: np.ndarray = np.zeros((0, 0))
    sdf_repetition_vector: np.ndarray = np.zeros((0))
    sdf_pass: List[str] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        sdf_actors = list(a for a in model.query_vertexes('sdf_actors'))
        sdf_channels = list(c for c in model.query_vertexes('sdf_channels'))
        sdf_topology = np.zeros(
            (len(sdf_channels), len(sdf_actors)),
            dtype=int
        )
        for row in model.query_view('sdf_topology'):
            a_index = next(idx for (idx, v) in enumerate(sdf_actors)
                           if v.identifier == row['actor_id'])
            c_index = next(idx for (idx, v) in enumerate(sdf_channels)
                           if v.identifier == row['channel_id'])
            sdf_topology[c_index, a_index] = int(row['tokens'])
        null_space = sympy.Matrix(sdf_topology).nullspace()
        if len(null_space) == 1:
            repetition_vector = mathutil.integralize_vector(null_space[0])
            repetition_vector = np.array(repetition_vector, dtype=int)
            initial_tokens = np.zeros((sdf_topology.shape[0], 1))
            schedule = sdfapi.get_PASS(sdf_topology,
                                       repetition_vector,
                                       initial_tokens)
            if schedule != []:
                sdf_pass = [sdf_actors[idx] for idx in schedule]
                return (
                    True,
                    SDFExecution(
                        sdf_actors,
                        sdf_channels,
                        sdf_topology,
                        repetition_vector,
                        sdf_pass
                    )
                )
        else:
            return (False, None)


@dataclass
class SDFToOrders(DecisionModel, MinizincAble):

    sdf_exec_sub: SDFExecution
    orderings: List[Vertex] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        sdf_exec_sub = next(
            (p for p in identified if isinstance(p, SDFExecution)),
            None)
        if sdf_exec_sub:
            orderings = list(o for o in model.query_vertexes('orderings'))
            if orderings:
                return (
                    True,
                    SDFToOrders(
                        sdf_exec_sub,
                        orderings
                    )
                )
            else:
                return (True, None)
        else:
            return (False, None)

    def populate_mzn_model(self, mzn):
        mzn['sdf_actors'] = range(1, len(self['sdf_actors'])+1)
        mzn['sdf_channels'] = range(1, len(self['sdf_channels'])+1)
        mzn['max_steps'] = len(self['sdf_pass'])
        cloned_firings = np.array([
            self['sdf_repetition_vector'].transpose()
            for i in range(1, len(self['sdf_channels'])+1)
        ])
        mzn['max_tokens'] = np.amax(
            cloned_firings * np.absolute(self['sdf_topology'])
        )
        mzn['activations'] = self['sdf_repetition_vector'][:, 0].tolist()
        mzn['static_orders'] = range(1, len(self.orderings)+1)
        return mzn

    def get_mzn_model_name(self):
        return 'sdf_order_linear_dmodel.mzn'

    def rebuild_forsyde_model(self, results):
        print(results['send'])
        print(results['mapped'])
        return ForSyDeModel()


@dataclass
class SDFToMultiCore(DecisionModel, MinizincAble):

    sdf_orders_sub: SDFToOrders
    cores: List[Vertex] = field(default_factory=lambda: [])
    bus: Optional[Vertex] = None

    @classmethod
    def identify(cls, model, identified):
        sdf_to_slot_sub = next(
            (p for p in identified if isinstance(p, SDFToOrders)),
            None)
        if sdf_to_slot_sub:
            cores = list(
                c for c in model.query_vertexes('tdma_mpsoc_processing_units')
            )
            busses = [b for b in model.query_vertexes('tdma_mpsoc_bus')]
            if len(cores) + len(busses) == len(sdf_to_slot_sub.orderings)\
                    and len(busses) == 1:
                return (
                    True,
                    SDFToMultiCore(
                        sdf_to_slot_sub,
                        cores,
                        busses[0],
                    )
                )
            else:
                return (True, None)
        else:
            return (False, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def populate_mzn_model(self, mzn):
        '''
        models pieces that are filled:

        % model parameters-
        int: max_bus_slots;
        int: max_tokens;
        int: max_steps;

        set of int: sdf_actors; % not flattened
        set of int: sdf_channels;
        set of int: processing_units;

        array[sdf_channels] of int: initial_tokens;
        array[sdf_actors] of int: activations;
        array[sdf_channels, sdf_actors] of int: sdf_topology;
        array[sdf_actors, processing_units] of int: wcet;
        % this numbers are 'per channel token'
        array[sdf_channels, processing_units, processing_units] of int: wcct;
        array[sdf_channels, processing_units] of int: send_overhead;
        array[sdf_channels, processing_units] of int: read_overhead;
        '''
        mzn['max_bus_slots'] = int(self.bus.properties['slots'])
        mzn['max_steps'] = len(self['sdf_actors'])
        cloned_firings = np.array([
            self['sdf_repetition_vector'].transpose()
            for i in range(1, len(self['sdf_channels'])+1)
        ])
        max_tokens = np.amax(
            cloned_firings * np.absolute(self['sdf_topology'])
        )
        mzn['max_tokens'] = int(max_tokens)
        mzn['sdf_actors'] = range(1, len(self['sdf_actors'])+1)
        mzn['sdf_channels'] = range(1, len(self['sdf_channels'])+1)
        mzn['processing_units'] = range(1, len(self.cores)+1)
        # TODO: The semantics of prefixes must be captures and put here!
        # for the moment, this always assumes zero starting tokens
        mzn['initial_tokens'] = [0 for c in self['sdf_channels']]
        # vector is in column format
        mzn['activations'] = self['sdf_repetition_vector'][:, 0].tolist()
        mzn['sdf_topology'] = self['sdf_topology'].tolist()
        # almost unitary assumption
        mzn['wcet'] = (max_tokens * np.ones((
            len(self['sdf_actors']),
            len(self.cores)
        ), dtype=int)).tolist()
        mzn['wcct'] = (np.ones((
            len(self['sdf_channels']),
            len(self.cores),
            len(self.cores)
        ), dtype=int)).tolist()
        mzn['send_overhead'] = (np.zeros((
            len(self['sdf_channels']),
            len(self.cores)
        ), dtype=int)).tolist()
        mzn['read_overhead'] = (np.zeros((
            len(self['sdf_channels']),
            len(self.cores)
        ), dtype=int)).tolist()
        # TODO: find a way to hardcode this less, but curently
        # it is a bijection with the number of objs supported
        # in the model. There's only 2 for now.
        mzn['objective_weights'] = [0, 0]
        return mzn

    def rebuild_forsyde_model(self, results):
        '''
        rebuild from the following variables:

        % variables
        array[sdf_channels, processing_units, steps0] of var 0..max_tokens: buffer;
        array[sdf_channels, processing_units, processing_units, bus_slots, steps0] of var 0..max_tokens: send;
        array[sdf_actors, processing_units, steps] of var 0..max(activations): mapped_actors;
        array[processing_units, steps0] of var int: cpu_time;
        array[steps0] of var int: bus_slots_used;
        '''
        new_model = self.covered_model()
        for (aidx, a) in enumerate(results['mapped_actors']):
            actor = self['sdf_actors'][aidx]
            for (pidx, p) in enumerate(a):
                ordering = self['orderings'][pidx]
                core = self.cores[pidx]
                for (t, v) in enumerate(p):
                    if 0 < v and v < 2:
                        # TODO: fix multiple addition of elements here
                        if not new_model.has_edge(ordering, core):
                            edge = Edge(
                                ordering,
                                core,
                                None,
                                None,
                                TypesFactory.build_type('Mapping')
                            )
                            new_model.add_edge(
                                ordering,
                                core,
                                object=edge
                            )
                        if not new_model.has_edge(actor, ordering):
                            ord_port = Port(
                                identifier = f'slot{t}',
                                port_type = TypesFactory.build_type('OrderedExecution')
                            )
                            ordering.ports.add(ord_port)
                            act_port = Port(
                                identifier = 'host',
                                port_type = TypesFactory.build_type('Host')
                            )
                            actor.ports.add(act_port)
                            edge = Edge(
                                actor,
                                ordering,
                                act_port,
                                ord_port,
                                TypesFactory.build_type('Scheduling')
                            )
                            new_model.add_edge(
                                ordering,
                                core,
                                object=edge
                            )
                    elif v > 1:
                        raise ValueError("Solution with pass must be implemented")
        for (cidx, c) in enumerate(results['send']):
            channel = self['sdf_channels'][cidx]
            for (pidx, p) in enumerate(c):
                sender = self.cores[pidx]
                for (ppidx, pp) in enumerate(p):
                    reciever = self.cores[ppidx]
                    for (sidx, s) in enumerate(pp):
                        for (t, v) in enumerate(s):
                            pass
        return new_model


@dataclass
class SDFToMultiCoreCharacterized(DecisionModel, MinizincAble):

    sdf_to_mpsoc_sub: SDFToMultiCore
    wcet: np.ndarray = np.array((0, 0), dtype=int)
    wcct: np.ndarray = np.array((0, 0, 0), dtype=int)
    send_overhead: np.ndarray = np.array((0, 0), dtype=int)
    read_overhead: np.ndarray = np.array((0, 0), dtype=int)

    def __init__(self):
        pass

    def identify(model, idenfitied):
        return (True, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel"

    def populate_mzn_model(mzn):
        return mzn


def _get_standard_problems() -> Iterable[Type[DecisionModel]]:
    return set(c for c in DecisionModel.__subclasses__())


def identify_decision_models(
    model: ForSyDeModel,
    problems: Set[Type[DecisionModel]] = _get_standard_problems()
) -> List[DecisionModel]:
    '''
    This function runs the Design Space Identification scheme,
    as presented in paper [DSI-DATE'2021], so that problems can
    be automatically solved from the given input model.

    If the argument **problems** is not passed,
    the API uses all subclasses found during runtime that implement
    the interfaces DecisionModel and Explorer.
    '''
    max_iterations = len(model) + len(problems)
    candidates = [p for p in problems]
    identified = []
    iterations = 0
    while len(candidates) > 0 and iterations < max_iterations:
        trials = ((c, c.identify(model, identified)) for c in candidates)
        for (c, (fixed, subprob)) in trials:
            # join with the identified
            if subprob:
                identified.append(subprob)
            # take away candidates at fixpoint
            if fixed:
                candidates.remove(c)
        iterations += 1
    return identified


def identify_decision_models_parallel(
    model: ForSyDeModel,
    problems: Set[Type[DecisionModel]] = set(),
    concurrent_idents: int = os.cpu_count()-1
) -> List[DecisionModel]:
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
    max_iterations = len(model) + len(problems)
    candidates = [p for p in problems]
    identified = []
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


def choose_decision_models(
    models: List[DecisionModel],
    criteria: ChoiceCriteria = ChoiceCriteria.DOMINANCE
) -> List[DecisionModel]:
    if criteria & ChoiceCriteria.DOMINANCE:
        dominant = []
        length = 0
        length_before = None
        while length != length_before:
            length_before = length
            dominant = [m for m in models if any(
               m.dominates(o) for o in models if o != m
            ) and not any(
               o.dominates(m) for o in dominant if o != m
            )]
            length = len(dominant)
        return dominant
    else:
        return models


async def identify_decision_models_async(
    model: ForSyDeModel,
    problems: Set[Type[DecisionModel]] = set()
) -> List[DecisionModel]:
    '''
    AsyncIO version of the same function. Wraps the non-async version.
    '''
    return identify_decision_models(model, problems)


async def identify_decision_models_parallel_async(
    model: ForSyDeModel,
    problems: Set[Type[DecisionModel]] = set(),
    concurrent_idents: int = os.cpu_count()-1
) -> List[DecisionModel]:
    '''
    AsyncIO version of the same function. Wraps the non-async version.
    '''
    return identify_decision_models_parallel(
        model,
        problems,
        concurrent_idents
    )

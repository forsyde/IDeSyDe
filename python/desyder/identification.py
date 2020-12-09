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
    busses: List[Vertex] = field(default_factory=lambda: [])
    connections: List[Edge] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_to_slot_sub = next(
            (p for p in identified if isinstance(p, SDFToOrders)),
            None)
        if sdf_to_slot_sub:
            cores = list(model.query_vertexes('tdma_mpsoc_procs'))
            busses = list(model.query_vertexes('tdma_mpsoc_bus'))
            # this strange code access the in memory vertexes
            # representation my going through the labels (ids)
            # first, hence the get_vertex function.
            connections = []
            for core in cores:
                for (v, adjdict) in model.adj[core].items():
                    for (n, e) in adjdict.items():
                        eobj = e['object']
                        if v in busses and eobj not in connections:
                            connections.append(eobj)
            for bus in busses:
                for (v, adjdict) in model.adj[bus].items():
                    for (n, e) in adjdict.items():
                        eobj = e['object']
                        if v in cores and eobj not in connections:
                            connections.append(eobj)
            if len(cores) + len(busses) >= len(sdf_to_slot_sub.orderings):
                res = SDFToMultiCore(
                    sdf_to_slot_sub,
                    cores,
                    busses,
                    connections
                )
        if res:
            return (True, res)
        elif not res and sdf_to_slot_sub:
            return (True, None)
        else:
            return (False, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def numbered_hw_units(self) -> Dict[str, int]:
        """
        """
        numbers = {}
        cur = 0
        for p in self.cores:
            numbers[p.identifier] = cur
            cur += 1
        for p in self.busses:
            numbers[p.identifier] = cur
            cur += 1
        return numbers

    def expanded_hw_units(self) -> Tuple[
            Dict[str, List[str]], Dict[str, int], Dict[str, int], Dict[str, int]
            ]:
        """Returns HW vertexes expasions for this decision model.

        Returns:
            A tuple that contains the _identifiers_ of the elements in the
            forsyde model relating before and after expansion. In particular,
            TDMA busses are expanded to each TDM slot as different elements.
            This eases reasoning about multiple level of communication.
        """
        # enumerate the processors in the model
        units_enum = {
            p.identifier: i for (i, p) in enumerate(self.cores)
        }
        # for homgeneity, also expand the processors
        # to themselves
        expansions = {p: [p] for p in units_enum}
        cores_enum = {p: i for (p, i) in units_enum.items()}
        # expand all TDMAs to their slot elements
        comm_enum = dict()
        units_enum_index = len(units_enum)
        for (i, bus) in enumerate(self.busses):
            expansions[bus.identifier] = []
            for s in range(bus.properties['slots']):
                units_enum[f'{bus.identifier}_slot_{s}'] = units_enum_index
                comm_enum[f'{bus.identifier}_slot_{s}'] = units_enum_index
                units_enum_index += 1
                expansions[bus.identifier].append(f'{bus.identifier}_slot_{s}')
        # unify processors and bus slots
        return (expansions, units_enum, cores_enum, comm_enum)

    def populate_mzn_model_nowcet(self, mzn):
        (expansions, units_enum, cores_enum, comm_enum) = self.expanded_hw_units()
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
        mzn['procs'] = set(i+1 for i in cores_enum.values())
        mzn['comm_units'] = set(i+1 for i in comm_enum.values())
        mzn['units_neighs'] = [
            set(
                units_enum[ex]
                for e in self.connections
                for ex in expansions[e.target_vertex.identifier]
                if e.source_vertex.identifier == u
            ).union(
            set(
                units_enum[ex]
                for e in self.connections
                for ex in expansions[e.source_vertex.identifier]
                if e.target_vertex.identifier == u
            ))
            for (u, uidx) in units_enum.items()
        ]
        # TODO: The semantics of prefixes must be captures and put here!
        # for the moment, this always assumes zero starting tokens
        mzn['initial_tokens'] = [0 for c in self['sdf_channels']]
        # vector is in column format
        mzn['activations'] = self['sdf_repetition_vector'][:, 0].tolist()
        mzn['sdf_topology'] = self['sdf_topology'].tolist()
        return mzn

    def populate_mzn_model(self, mzn):
        # use the general method without faking data
        self.populate_mzn_model_nowcet(mzn)
        # almost unitary assumption
        mzn['wcet'] = (mzn['max_tokens'] * np.ones((
            len(self['sdf_actors']),
            len(self.cores)
        ), dtype=int)).tolist()
        mzn['wcct'] = (np.ones((
            len(self['sdf_channels']),
            len(mzn['procs']) + len(mzn['comm_units']),
            len(mzn['procs']) + len(mzn['comm_units'])
        ), dtype=int)).tolist()
        # mzn['send_overhead'] = (np.zeros((
        #     len(self['sdf_channels']),
        #     len(self.cores)
        # ), dtype=int)).tolist()
        # mzn['read_overhead'] = (np.zeros((
        #     len(self['sdf_channels']),
        #     len(self.cores)
        # ), dtype=int)).tolist()
        # since we are using the same minizinc model for more than
        # one decision model, we have to force it all zero here.
        mzn['objective_weights'] = [0, 0]
        return mzn

    def rebuild_forsyde_model(self, results):
        '''
        rebuild from the following variables:

        % variables
        array[sdf_channels, procs, steps0] of var 0..max_tokens: buffer;
        array[sdf_channels, procs, procs, bus_slots, steps0] of var 0..max_tokens: send;
        array[sdf_actors, procs, steps] of var 0..max(activations): mapped_actors;
        array[procs, steps0] of var int: cpu_time;
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
                                actor,
                                ordering,
                                object=edge
                            )
                    elif v > 1:
                        raise ValueError("Solution with pass must be implemented")
        # for (cidx, c) in enumerate(results['send']):
        #     channel = self['sdf_channels'][cidx]
        #     for (pidx, p) in enumerate(c):
        #         sender = self.cores[pidx]
        #         for (ppidx, pp) in enumerate(p):
        #             reciever = self.cores[ppidx]
        #             for (t, v) in enumerate(pp):
        #                 pass
        return new_model


@dataclass
class SDFToMultiCoreCharacterized(DecisionModel, MinizincAble):

    sdf_to_mpsoc_sub: SDFToMultiCore
    wcet: np.ndarray = np.array((0, 0), dtype=int)
    wcct: np.ndarray = np.array((0, 0, 0), dtype=int)
    throughput_importance: int = 0
    latency_importance: int = 0
    send_overhead: np.ndarray = np.array((0, 0), dtype=int)
    read_overhead: np.ndarray = np.array((0, 0), dtype=int)

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_to_mpsoc_sub = next(
            (p for p in identified if isinstance(p, SDFToMultiCore)),
            None)
        if sdf_to_mpsoc_sub:
            sdf_actors = sdf_to_mpsoc_sub['sdf_actors']
            cores = sdf_to_mpsoc_sub['cores']
            wcet = None
            wcct = None
            if next(model.query_view('count_wcet'))['count'] ==\
                    len(cores) * len(sdf_actors):
                wcet = np.zeros(
                    (
                        len(cores),
                        len(sdf_actors),
                    ),
                    dtype=int
                )
                for row in model.query_view('wcet'):
                    app_index = next(
                        idx for (idx, v) in enumerate(sdf_actors)
                        if v.identifier == row['app_id']
                    )
                    plat_index = next(
                        idx for (idx, v) in enumerate(cores)
                        if v.identifier == row['plat_id']
                    )
                    wcet[app_index, plat_index] = int(row['wcet_time'])
            sdf_channels = sdf_to_mpsoc_sub['sdf_channels']
            busses = sdf_to_mpsoc_sub['busses']
            units = cores + busses
            connections = sdf_to_mpsoc_sub['connections']
            if next(model.query_view('count_signal_wcct'))['count'] ==\
                    len(sdf_channels) * 2 * len(connections):
                wcct = np.zeros((len(sdf_channels), len(units), len(units)), dtype=int)
                for row in model.query_view('signal_wcct'):
                    sender_index = next(
                        idx for (idx, v) in enumerate(units)
                        if v.identifier == row['sender_id']
                    )
                    reciever_index = next(
                        idx for (idx, v) in enumerate(units)
                        if v.identifier == row['reciever_id']
                    )
                    signal_index = next(
                        idx for (idx, v) in enumerate(sdf_channels)
                        if v.identifier == row['signal_id']
                    )
                    wcct[signal_index, sender_index, reciever_index] = int(row['wcct_time'])
            # although there should be only one Th vertex
            # per application, we apply maximun just in case
            # someone forgot to make sure there is only one annotation
            # per application
            throughput_importance = 0
            throughput_targets = list(model.query_vertexes('min_throughput_targets'))
            if all(v in throughput_targets for v in sdf_to_mpsoc_sub['sdf_actors']):
                throughput_importance = max(
                    (int(v.properties['apriori_importance'])
                     for v in model.query_vertexes('min_throughput')),
                    default=0
                )
            if wcet is not None and wcct is not None:
                res = cls(
                    sdf_to_mpsoc_sub=sdf_to_mpsoc_sub,
                    wcet=wcet,
                    wcct=wcct,
                    throughput_importance=throughput_importance,
                    latency_importance=0
                )
        if res:
            return (True, res)
        elif sdf_to_mpsoc_sub and not res:
            return (True, None)
        else:
            return (False, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def numbered_hw_units(self):
        return self.sdf_to_mpsoc_sub.numbered_hw_units()

    def expanded_hw_units(self):
        return self.sdf_to_mpsoc_sub.expanded_hw_units()

    def populate_mzn_model(self, mzn):
        # use the non faked part of the covered problem
        # to save some code
        pre_units_enum = self.sdf_to_mpsoc_sub.numbered_hw_units()
        (expansions, units_enum, cores_enum, comm_enum) = self.sdf_to_mpsoc_sub.expanded_hw_units()
        cores_enum = {}
        cores_expansions = {}
        for (e, el) in expansions.items():
            if any(p.identifier == e for p in self['cores']):
                cores_expansions[e] = el
                for ex in el:
                    cores_enum[ex] = units_enum[ex]
        comm_enum = {}
        comm_expansions = {}
        for (e, el) in expansions.items():
            if any(p.identifier == e for p in self['busses']):
                comm_expansions[e] = el
                for ex in el:
                    comm_enum[ex] = units_enum[ex]
        self.sdf_to_mpsoc_sub.populate_mzn_model_nowcet(mzn)
        wcet_expanded = np.zeros(
            (
                len(self['sdf_actors']),
                sum(len(el) for el in cores_expansions.values())
            ),
            dtype=int
        )
        for (aidx, a) in enumerate(self['sdf_actors']):
            for (eidx, e) in enumerate(self['cores']):
                for ex in expansions[e.identifier]:
                    exidx = units_enum[ex]
                    wcet_expanded[aidx, exidx] = self.wcet[aidx, eidx]
        wcct_expanded = np.zeros(
            (
                len(self['sdf_channels']),
                sum(len(el) for el in expansions.values()),
                sum(len(el) for el in expansions.values())
            ),
            dtype=int
        )
        for (cidx, c) in enumerate(self['sdf_channels']):
            for (e, eidx) in pre_units_enum.items():
                for (e2, e2idx) in pre_units_enum.items():
                    for ex in expansions[e]:
                        exidx = units_enum[ex]
                        for ex2 in expansions[e2]:
                            ex2idx = units_enum[ex2]
                            wcct_expanded[cidx, exidx, ex2idx] = self.wcct[cidx, eidx, e2idx]
        mzn['wcet'] = wcet_expanded.tolist()
        mzn['wcct'] = wcct_expanded.tolist()
        # mzn['send_overhead'] = (np.zeros((
        #     len(self['sdf_channels']),
        #     len(self['cores'])
        # ), dtype=int)).tolist()
        # mzn['read_overhead'] = (np.zeros((
        #     len(self['sdf_channels']),
        #     len(self['cores'])
        # ), dtype=int)).tolist()
        mzn['objective_weights'] = [
            self.throughput_importance,
            self.latency_importance
        ]
        return mzn

    def rebuild_forsyde_model(self, results):
        print(results['mapped_actors'])
        print(results['objective'])
        print(results['time'])
        new_model = self.covered_model()
        for (aidx, a) in enumerate(results['mapped_actors']):
            actor = self['sdf_actors'][aidx]
            for (pidx, p) in enumerate(a):
                ordering = self['orderings'][pidx]
                core = self['cores'][pidx]
                for (t, v) in enumerate(p):
                    if 0 < v and v < 2:
                        # TODO: fix multiple addition of elements here
                        if not new_model.has_edge(core, ordering):
                            exec_port = Port(
                                'execution',
                                TypesFactory.build_type('AbstractGrouping'),
                            )
                            core.ports.add(exec_port)
                            new_model.add_edge(
                                core,
                                ordering,
                                object=Edge(
                                    core,
                                    ordering,
                                    exec_port,
                                    None,
                                    TypesFactory.build_type('Mapping')
                                )
                            )
                        if not new_model.has_edge(ordering, actor):
                            ord_port = Port(
                                identifier = f'slot[{t}]',
                                port_type = TypesFactory.build_type('Process')
                            )
                            ordering.ports.add(ord_port)
                            new_model.add_edge(
                                ordering,
                                actor,
                                object=Edge(
                                    ordering,
                                    actor,
                                    ord_port,
                                    None,
                                    TypesFactory.build_type('Scheduling')
                                )
                            )
                    elif v > 1:
                        raise ValueError("Solution with pass must be implemented")
        (expansions, enum_items) = self.sdf_to_mpsoc_sub.expanded_hw_units()
        items_enums = {i: p for (i, p) in enum_items.items()}
        for (cidx, c) in enumerate(results['send']):
            channel = self['sdf_channels'][cidx]
            for (pidx, p) in enumerate(c):
                # sender = self['cores'][pidx]
                for (ppidx, pp) in enumerate(p):
                    # reciever = self['cores'][ppidx]
                    for (t, v) in enumerate(pp):
                        pass
        return new_model

@dataclass
class SDFToMultiCoreCharacterizedJobs(DecisionModel, MinizincAble):

    sdf_mpsoc_char_sub: SDFToMultiCoreCharacterized
    jobs: List[str] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_mpsoc_char_sub = next(
            (p for p in identified if isinstance(p, SDFToMultiCoreCharacterized)),
            None)
        if sdf_mpsoc_char_sub:
            jobs = {
                f"{a.identifier}_{i}": (a.identifier, aidx)
                for (aidx, a) in enumerate(sdf_mpsoc_char_sub['sdf_actors'])
                for i in sdf_mpsoc_char_sub['sdf_repetition_vector'][aidx]
            }
            res = cls(
                sdf_mpsoc_char_sub=sdf_mpsoc_char_sub,
                jobs=jobs
            )
        if res:
            return (True, res)
        else:
            return (False, None)

    def get_mzn_model_name(self):
        return "sdf_job_scheduling.mzn"

    def populate_mzn_model(self, mzn):
        # use the non faked part of the covered problem
        # to save some code
        pre_units_enum = self.sdf_mpsoc_char_sub.numbered_hw_units()
        (expansions, units_enum, cores_enum, comm_enum) = self.sdf_mpsoc_char_sub.expanded_hw_units()
        cores_enum = {}
        cores_expansions = {}
        for (e, el) in expansions.items():
            if any(p.identifier == e for p in self['cores']):
                cores_expansions[e] = el
                for ex in el:
                    cores_enum[ex] = units_enum[ex]
        comm_enum = {}
        comm_expansions = {}
        for (e, el) in expansions.items():
            if any(p.identifier == e for p in self['busses']):
                comm_expansions[e] = el
                for ex in el:
                    comm_enum[ex] = units_enum[ex]
        wcet_expanded = np.zeros(
            (
                len(self['sdf_actors']),
                sum(len(el) for el in cores_expansions.values())
            ),
            dtype=int
        )
        for (aidx, a) in enumerate(self['sdf_actors']):
            for (eidx, e) in enumerate(self['cores']):
                for ex in expansions[e.identifier]:
                    exidx = units_enum[ex]
                    wcet_expanded[aidx, exidx] = self['wcet'][aidx, eidx]
        wcct_expanded = np.zeros(
            (
                len(self['sdf_channels']),
                sum(len(el) for el in expansions.values()),
                sum(len(el) for el in expansions.values())
            ),
            dtype=int
        )
        for (cidx, c) in enumerate(self['sdf_channels']):
            for (e, eidx) in pre_units_enum.items():
                for (e2, e2idx) in pre_units_enum.items():
                    for ex in expansions[e]:
                        exidx = units_enum[ex]
                        for ex2 in expansions[e2]:
                            ex2idx = units_enum[ex2]
                            wcct_expanded[cidx, exidx, ex2idx] = self['wcct'][cidx, eidx, e2idx]
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
        mzn['procs'] = set(i+1 for i in cores_enum.values())
        mzn['comm_units'] = set(i+1 for i in comm_enum.values())
        mzn['jobs'] = range(1, len(self.jobs)+1)
        # mzn['activations'] = self['sdf_repetition_vector'][:, 0].tolist()
        mzn['jobs_actors'] = [
            aidx+1
            for (j, (label, aidx)) in self.jobs.items()
        ]
        # TODO: must fix the delays in the model
        mzn['initial_tokens'] = [0 for a in self['sdf_actors']]
        mzn['sdf_topology'] = self['sdf_topology'].tolist()
        mzn['wcet'] = wcet_expanded.tolist()
        mzn['wcct'] = wcct_expanded.tolist()
        mzn['units_neighs'] = [
            set(
                units_enum[ex]
                for e in self['connections']
                for ex in expansions[e.target_vertex.identifier]
                if e.source_vertex.identifier == u
            ).union(
            set(
                units_enum[ex]
                for e in self['connections']
                for ex in expansions[e.source_vertex.identifier]
                if e.target_vertex.identifier == u
            ))
            for (u, uidx) in units_enum.items()
        ]
        # mzn['send_overhead'] = (np.zeros((
        #     len(self['sdf_channels']),
        #     len(self['cores'])
        # ), dtype=int)).tolist()
        # mzn['read_overhead'] = (np.zeros((
        #     len(self['sdf_channels']),
        #     len(self['cores'])
        # ), dtype=int)).tolist()
        mzn['objective_weights'] = [
            self['throughput_importance'],
            self['latency_importance']
        ]
        return mzn

    def rebuild_forsyde_model(self, results):
        new_model = self.covered_model()
        print(results)
        return new_model


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
    max_iterations = len(model) * len(problems)
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
    max_iterations = len(model) * len(problems)
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
        non_dominated = [m for m in models]
        for m in models:
            for other in models:
                if m in non_dominated and m != other and other.dominates(m):
                    non_dominated.remove(m)
        return non_dominated
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

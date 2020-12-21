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
import math
import os
from dataclasses import dataclass, field
from enum import Flag, auto
from typing import List, Union, Tuple, Set, Optional, Dict, Type, Iterable, Any

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

import idesyde.math as mathutil
import idesyde.sdf as sdfapi


def print_list(d, s=""):
    if type(d) == list:
        for (i, v) in enumerate(d):
            print_list(v, s=s+f"{i} ")
    elif d:
        print(f"{s}: {d}")


class ChoiceCriteria(Flag):
    '''Flag to indicate decision model subsumption
    '''
    DOMINANCE = auto()


@dataclass
class DecisionModel(object):

    """Decision Models interface for the DSI procedure.

    A dict like interface is implemented for decision models
    for convenience, which recursively checks any other partial
    identifications that the model may have.
    """

    @classmethod
    def identify(
            cls,
            model: ForSyDeModel,
            subproblems: List["DecisionModel"]
    ) -> Tuple[bool, Optional["DecisionModel"]]:
        """Perform identification procedure and obtain a new Decision Model

        This class function analyses the given design model (ForSyDe Model)
        and returns a decision model which partially idenfity it. It
        indicates when it can still be executed or not via a tuple.

        Arguments:
            model: Input ForSyDe model.
            subproblems: Decision Models that have already been identified.

        Returns:
            A tuple where the first element indicates if any decision model
            belonging to 'cls' can still be identified and a decision model
            that partially identifies 'model' in the second element.
        """
        return (True, None)

    def __hash__(self):
        return hash((self.covered_vertexes(), self.covered_edges()))

    def short_name(self) -> str:
        return str(self.__class__.__name__)

    def compute_deduced_properties(self) -> None:
        '''Compute deducible properties for this decision model'''
        raise NotImplementedError

    def covered_vertexes(self) -> Iterable[Vertex]:
        '''Get vertexes partially identified by the Decision Model.

        Returns:
            Iterable for the vertexes.
        '''
        raise NotImplementedError

    def covered_edges(self) -> Iterable[Edge]:
        '''Get edges partially identified by the Decision Model.

        Returns:
            Iterable for the edges.
        '''
        return []

    def covered_model(self) -> ForSyDeModel:
        '''Returns the covered ForSyDe Model.
        Returns:
            A copy of the vertexes and edges that this decision
            model partially identified.
        '''
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
        the other. It also takes in consideration the explicit
        model domination set from 'self'.

        Args:
            other: the other decision model to be checked.

        Returns:
            True if 'self' dominates other. False otherwise.
        '''
        # other - self
        vertexes_other = set(other.covered_vertexes())
        vertexes_self = set(self.covered_vertexes())
        edges_other = set(other.covered_edges())
        edges_self = set(self.covered_edges())
        # other is fully contained in self and itersection is consistent
        return len(vertexes_self.difference(vertexes_other)) > 0\
            or len(edges_self.difference(edges_other)) > 0


class MinizincAble(object):
    '''Interface that enables consumption by minizinc-based solvers.
    '''

    def get_mzn_data(self) -> Dict[str, Any]:
        '''Build the input minizinc dictionary

        As the minizinc library has a dict-like interface but
        is immutable once a value is set, this pre-filling diciotnary
        is passed aroudn before feeding the minizinc interface for
        better code reuse.

        Returns:
            A dictionary containing all the data necessary to run
            the minizinc model attached to this decision model.
        '''
        return dict()

    def populate_mzn_model(
            self,
            model: Union[MznModel, MznInstance]
    ) -> Union[MznModel, MznInstance]:
        data_dict = self.get_mzn_data()
        for k in data_dict:
            model[k] = data_dict[k]
        return model

    def get_mzn_model_name(self) -> str:
        '''Get the number of the minizinc file for this class.

        Returns:
            the name of the file that represents this decision model.
            Although a method, it is expected that the string return
            is constant, i.e. static.
        '''
        return ""

    def rebuild_forsyde_model(
        self,
        result: MznResult
    ) -> ForSyDeModel:
        return ForSyDeModel()

    def build_mzn_model(self, mzn=MznModel()):
        model_txt = resources.read_text(
            'idesyde.minizinc',
            self.get_mzn_model_name()
        )
        mzn.add_string(model_txt)
        self.populate_mzn_model(mzn)
        return mzn


class IdentificationRule(abc.ABCMeta):

    def __call__(
            self,
            model: ForSyDeModel,
            subproblems: Set[DecisionModel]
    ) -> Tuple[bool, Optional[DecisionModel]]:
        return self.identify(model, subproblems)

    @abc.abstractmethod
    def identify(
            model: ForSyDeModel,
            subproblems: Set[DecisionModel]
    ) -> Tuple[bool, Optional[DecisionModel]]:
        return (True, None)


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

    sdf_actors: Set[Vertex] = field(default_factory=set)
    sdf_channels: Set[Vertex] = field(default_factory=set)
    sdf_topology: np.ndarray = np.zeros((0, 0))
    sdf_repetition_vector: np.ndarray = np.zeros((0))
    sdf_pass: List[Vertex] = field(default_factory=list)

    sdf_max_tokens: np.ndarray = np.zeros((0))

    sdf_actors_enum: Dict[Vertex, int] = field(default_factory=dict)
    sdf_channels_enum: Dict[Vertex, int] = field(default_factory=dict)

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_actors = set(a for a in model.query_vertexes('sdf_actors'))
        sdf_channels = set(c for c in model.query_vertexes('sdf_channels'))
        sdf_actors_enum = {k: i for (i, k) in enumerate(sdf_actors)}
        sdf_channels_enum = {k: i for (i, k) in enumerate(sdf_channels)}
        inv_sdf_actors_enum = dict(enumerate(sdf_actors))
        sdf_topology = np.zeros(
            (len(sdf_channels), len(sdf_actors)),
            dtype=int
        )
        for row in model.query_view('sdf_topology'):
            a_index = next(idx for (a, idx) in sdf_actors_enum.items()
                           if a.identifier == row['actor_id'])
            c_index = next(idx for (c, idx) in sdf_channels_enum.items()
                           if c.identifier == row['channel_id'])
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
                sdf_pass = [inv_sdf_actors_enum[idx] for idx in schedule]
                res = SDFExecution(
                    sdf_actors=sdf_actors,
                    sdf_channels=sdf_channels,
                    sdf_topology=sdf_topology,
                    sdf_repetition_vector=repetition_vector,
                    sdf_pass=sdf_pass,
                    sdf_actors_enum=sdf_actors_enum,
                    sdf_channels_enum=sdf_channels_enum
                )
        # conditions for fixpoints and partial identification
        if res:
            res.compute_deduced_properties()
            return (True, res)
        else:
            return (False, None)

    def covered_vertexes(self):
        yield from self.sdf_actors
        yield from self.sdf_channels

    def compute_deduced_properties(self):
        self.max_tokens = np.zeros((len(self.sdf_channels)), dtype=int)
        for (cidx, c) in enumerate(self.sdf_channels):
            self.max_tokens[cidx] = max(
                self.sdf_topology[cidx, aidx] * self.sdf_repetition_vector[aidx]
                for (aidx, a) in enumerate(self.sdf_actors)
            )


@dataclass
class SDFToOrders(DecisionModel, MinizincAble):

    # sub identifications
    sdf_exec_sub: SDFExecution = SDFExecution()

    # partial identification
    orderings: Set[Vertex] = field(default_factory=set)

    orderings_enum: Dict[Vertex, int] = field(default_factory=dict)

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_exec_sub = next(
            (p for p in identified if isinstance(p, SDFExecution)),
            None)
        if sdf_exec_sub:
            orderings = set(o for o in model.query_vertexes('orderings'))
            if orderings:
                res = SDFToOrders(
                    sdf_exec_sub=sdf_exec_sub,
                    orderings=orderings
                )
        # conditions for fixpoints and partial identification
        if res:
            res.compute_deduced_properties()
            return (True, res)
        elif sdf_exec_sub and not res:
            return (True, None)
        else:
            return (False, None)

    def covered_vertexes(self):
        yield from self.orderings
        yield from self.sdf_exec_sub.covered_vertexes()

    def compute_deduced_properties(self):
        self.orderings_enum = {k: i for (i, k) in enumerate(self.orderings)}

    def get_mzn_data(self):
        data = dict()
        sub = self.sdf_exec_sub
        data['sdf_actors'] = range(1, len(sub.sdf_actors)+1)
        data['sdf_channels'] = range(1, len(sub.sdf_channels)+1)
        data['sdf_topology'] = sub.sdf_topology.tolist()
        data['max_steps'] = len(sub.sdf_pass) // len(self.orderings)
        data['max_steps'] += 1 if len(sub.sdf_pass) % len(self.orderings) > 0 else 0
        data['max_tokens'] = sub.max_tokens.tolist()
        data['activations'] = sub.sdf_repetition_vector[:, 0].tolist()
        data['static_orders'] = range(1, len(self.orderings)+1)
        # TODO: find a awya to compute the initial tokens
        # reliably
        data['initial_tokens'] = [0 for c in sub.sdf_channels]
        return data

    def get_mzn_model_name(self):
        return 'sdf_order_linear_dmodel.mzn'

    def rebuild_forsyde_model(self, results):
        return ForSyDeModel()


@dataclass
class SDFToMultiCore(DecisionModel, MinizincAble):

    # covered decision models
    sdf_orders_sub: SDFToOrders = SDFToOrders()

    # partially identified
    cores: Set[Vertex] = field(default_factory=set)
    busses: Set[Vertex] = field(default_factory=set)
    connections: List[Edge] = field(default_factory=list)

    # deduced properties
    vertex_expansions: Dict[Vertex, List[Vertex]] = field(
        default_factory=dict
    )
    edge_expansions: Dict[Edge, List[Edge]] = field(
        default_factory=dict
    )
    cores_enum: Dict[Vertex, int] = field(
        default_factory=dict
    )
    comm_enum: Dict[Vertex, int] = field(
        default_factory=dict
    )
    expanded_cores_enum: Dict[Vertex, int] = field(
        default_factory=dict
    )
    expanded_comm_enum: Dict[Vertex, int] = field(
        default_factory=dict
    )
    max_steps: int = 1

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_orders_sub = next(
            (p for p in identified if isinstance(p, SDFToOrders)),
            None)
        if sdf_orders_sub:
            cores = set(model.query_vertexes('tdma_mpsoc_procs'))
            busses = set(model.query_vertexes('tdma_mpsoc_bus'))
            # this strange code access the in memory vertexes
            # representation by going through the labels (ids)
            # first, hence the get_vertex function.
            connections = set()
            for core in cores:
                for (v, adjdict) in model.adj[core].items():
                    for (n, e) in adjdict.items():
                        eobj = e['object']
                        if v in busses and eobj not in connections:
                            connections.add(eobj)
            for bus in busses:
                for (v, adjdict) in model.adj[bus].items():
                    for (n, e) in adjdict.items():
                        eobj = e['object']
                        if v in cores and eobj not in connections:
                            connections.add(eobj)
            if len(cores) + len(busses) >= len(sdf_orders_sub.orderings):
                res = SDFToMultiCore(
                    sdf_orders_sub=sdf_orders_sub,
                    cores=cores,
                    busses=busses,
                    connections=connections
                )
        # conditions for fixpoints and partial identification
        if res:
            res.compute_deduced_properties()
            return (True, res)
        elif not res and sdf_orders_sub:
            return (True, None)
        else:
            return (False, None)

    def covered_vertexes(self):
        yield from self.cores
        yield from self.busses
        yield from self.sdf_orders_sub.covered_vertexes()

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def compute_deduced_properties(self):
        self.sdf_orders_sub.compute_deduced_properties()
        vertex_expansions = {p: set([p]) for p in self.cores}
        edge_expansions = {e: set() for e in self.connections}
        cores_enum = {p: i for (i, p) in enumerate(self.cores)}
        expanded_cores_enum = {p: i for (i, p) in enumerate(self.cores)}
        # expand all TDMAs to their slot elements
        comm_enum = dict()
        expanded_comm_enum = dict()
        units_enum_index = len(cores_enum)
        for (i, bus) in enumerate(self.busses):
            comm_enum[bus] = i + len(cores_enum)
            vertex_expansions[bus] = set()
            for s in range(bus.properties['slots']):
                bus_slot = Vertex(
                    identifier=f'{bus.identifier}_slot_{s}'
                )
                expanded_comm_enum[bus_slot] = units_enum_index
                vertex_expansions[bus].add(bus_slot)
                units_enum_index += 1
        firings = int(np.sum(self.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector))
        max_steps = firings // len(expanded_cores_enum)
        if firings % len(expanded_cores_enum) > 0:
            max_steps += 1
        # now go through all the connections and
        # create copies of them as necessary to accomodate
        # the newly created processor and comm elements
        for (v, l) in vertex_expansions.items():
            for (o, ol) in vertex_expansions.items():
                for e in self.connections:
                    if e.source_vertex == v and e.target_vertex == o:
                        for v_new in l:
                            for o_new in ol:
                                expanded_e = Edge(
                                    source_vertex=v_new,
                                    target_vertex=o_new,
                                    edge_type=e.edge_type
                                )
                                edge_expansions[e].add(expanded_e)
        self.vertex_expansions = vertex_expansions
        self.edge_expansions = edge_expansions
        self.expanded_cores_enum = expanded_cores_enum
        self.expanded_comm_enum = expanded_comm_enum
        self.cores_enum = cores_enum
        self.comm_enum = comm_enum
        self.max_steps = max_steps

    def get_mzn_data(self):
        data = self.sdf_orders_sub.get_mzn_data()
        expanded_units_enum = {
            **self.expanded_cores_enum,
            **self.expanded_comm_enum
        }
        data['procs'] = set(i+1 for i in self.expanded_cores_enum.values())
        data['comm_units'] = set(i+1 for i in self.expanded_comm_enum.values())
        data['units_neighs'] = [
            set(
                expanded_units_enum[ex.target_vertex]+1
                for (e, el) in self.edge_expansions.items()
                for ex in el
                if ex.source_vertex == u
            ).union(
                set(
                    expanded_units_enum[ex.source_vertex]+1
                    for (e, el) in self.edge_expansions.items()
                    for ex in el
                    if ex.target_vertex == u
                ))
            for (u, uidx) in expanded_units_enum.items()
        ]
        # since the minizinc model requires wcet and wcct,
        # we fake it with almost unitary assumption
        data['wcet'] = (data['max_tokens'] * np.ones((
            len(data['sdf_actors']),
            len(self.cores)
        ), dtype=int)).tolist()
        data['token_wcct'] = (np.ones((
            len(data['sdf_channels']),
            len(data['procs']) + len(data['comm_units'])
        ), dtype=int)).tolist()
        # since the minizinc model requires objective weights,
        # we just disconsder them
        data['objective_weights'] = [0, 0]
        # take away spurius extras
        data.pop('static_orders')
        return data

    def rebuild_forsyde_model(self, results):
        new_model = self.covered_model()
        max_steps = self.max_steps
        sdf_topology = self.sdf_orders_sub.sdf_exec_sub.sdf_topology
        sdf_actors_enum = self.sdf_orders_sub.sdf_exec_sub.sdf_actors_enum
        sdf_channels_enum = self.sdf_orders_sub.sdf_exec_sub.sdf_channels_enum
        inv_sdf_actors_enum = {i: k for (k, i) in sdf_actors_enum.items()}
        orderings_enum = self.sdf_orders_sub.orderings_enum
        cores_enum = self.cores_enum
        inv_orderings_enum = {i: k for (k, i) in orderings_enum.items()}
        for (core, pidx) in cores_enum.items():
            ordering = inv_orderings_enum[pidx]
            if not new_model.has_edge(core, ordering, key="object"):
                new_edge = Edge(
                    source_vertex=core,
                    target_vertex=ordering,
                    source_vertex_port=Port(
                        identifier="execution",
                        port_type=TypesFactory.build_type('AbstractOrdering')
                    )
                )
                new_model.add_edge(core, ordering, object=new_edge)
            slot = 0
            for t in range(max_steps):
                sdf_pass = sdfapi.get_PASS(
                    sdf_topology,
                    np.array([[
                        results["mapped_actors"][a][pidx][t]
                        for a in sdf_actors_enum.values()
                    ]]).transpose(),
                    np.array([[
                        results["buffer_start"][c][pidx][t]
                        for c in sdf_channels_enum.values()
                    ]]).transpose()
                )
                for aidx in sdf_pass:
                    actor = inv_sdf_actors_enum[aidx]
                    if not new_model.has_edge(ordering, actor, key="object"):
                        new_edge = Edge(
                            source_vertex=ordering,
                            target_vertex=actor,
                            source_vertex_port=Port(
                                identifier=f"slot[{slot}]",
                                port_type=TypesFactory.build_type("Process")
                            )
                        )
                        new_model.add_edge(ordering, actor, object=new_edge)
                        slot += 1
        comm_enum = self.comm_enum
        expanded_comm_enum = self.expanded_comm_enum
        vertex_expansions = self.vertex_expansions
        for (comm, commidx) in comm_enum.items():
            slot_expansions = vertex_expansions[comm]
            ordering = inv_orderings_enum[commidx]
            if not new_model.has_edge(comm, ordering, key="object"):
                new_edge = Edge(
                    source_vertex=comm,
                    target_vertex=ordering,
                    source_vertex_port=Port(
                        identifier="timeslots",
                        port_type=TypesFactory.build_type('AbstractOrdering')
                    )
                )
                new_model.add_edge(comm, ordering, object=new_edge)
            for (c, cidx) in sdf_channels_enum.items():
                for slot in slot_expansions:
                    # shift ocmmunication elemnets to zero due to to way minizinc
                    # return its results
                    slotidx = expanded_comm_enum[slot] - max(cores_enum.values()) - 1
                    if any((results['send_allocation'][cidx][p][pp][t][tt][slotidx] > 0
                            for p in cores_enum.values()
                            for pp in cores_enum.values()
                            for t in range(max_steps)
                            for tt in range(max_steps)))\
                            and not new_model.has_edge(ordering, c, key="object"):
                        new_edge = Edge(
                            source_vertex=ordering,
                            target_vertex=c,
                            source_vertex_port=Port(
                                identifier=f"slot[{slotidx}]"
                            )
                        )
                        new_model.add_edge(ordering, c, object=new_edge)
        return new_model


@dataclass
class SDFToMultiCoreCharacterized(DecisionModel, MinizincAble):

    # covered partial identifications
    sdf_mpsoc_sub: SDFToMultiCore = SDFToMultiCore()

    # elements that are partially identified
    wcet_vertexes: Set[Vertex] = field(default_factory=set)
    token_wcct_vertexes: Set[Vertex] = field(default_factory=set)
    goals_vertexes: Set[Vertex] = field(default_factory=set)
    wcet: np.ndarray = np.array((0, 0), dtype=int)
    token_wcct: np.ndarray = np.zeros((0, 0))
    throughput_importance: int = 0
    latency_importance: int = 0
    send_overhead: np.ndarray = np.array((0, 0), dtype=int)
    read_overhead: np.ndarray = np.array((0, 0), dtype=int)

    # deduced properties
    expanded_wcet: np.ndarray = np.array((0, 0), dtype=int)
    expanded_token_wcct: np.ndarray = np.zeros((0, 0), dtype=int)

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_mpsoc_sub = next(
            (p for p in identified if isinstance(p, SDFToMultiCore)),
            None)
        if sdf_mpsoc_sub:
            sdf_actors = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
            sdf_channels = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
            cores = sdf_mpsoc_sub.cores
            busses = sdf_mpsoc_sub.busses
            units = cores.union(busses)
            wcet = None
            token_wcct = None
            count_wcet: int = next(model.query_view('count_wcet'))['count']
            count_token_wcct: int = next(model.query_view('count_token_signal_wcct'))['count']
            if count_wcet == len(cores) * len(sdf_actors):
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
            if count_token_wcct == len(sdf_channels) * len(busses):
                token_wcct = np.zeros((len(sdf_channels), len(units)), dtype=int)
                for row in model.query_view('signal_token_wcct'):
                    signal_index = next(
                        idx for (idx, v) in enumerate(sdf_channels)
                        if v.identifier == row['signal_id']
                    )
                    comm_index = next(
                        idx for (idx, v) in enumerate(units)
                        if v.identifier == row['comm_id']
                    )
                    token_wcct[signal_index, comm_index] = int(row['wcct_time'])
            # although there should be only one Th vertex
            # per application, we apply maximun just in case
            # someone forgot to make sure there is only one annotation
            # per application
            goals_vertexes = set(model.query_vertexes('min_throughput'))
            throughput_importance = 0
            throughput_targets = list(model.query_vertexes('min_throughput_targets'))
            if all(v in throughput_targets for v in sdf_actors):
                throughput_importance = max(
                    (int(v.properties['apriori_importance'])
                     for v in model.query_vertexes('min_throughput')),
                    default=0
                )
            if wcet is not None and token_wcct is not None:
                res = cls(
                    sdf_mpsoc_sub=sdf_mpsoc_sub,
                    wcet_vertexes=set(model.query_vertexes('wcet')),
                    token_wcct_vertexes=set(model.query_vertexes('signal_token_wcct')),
                    wcet=wcet,
                    token_wcct=token_wcct,
                    throughput_importance=throughput_importance,
                    latency_importance=0,
                    goals_vertexes=goals_vertexes
                )
        if res:
            res.compute_deduced_properties()
            return (True, res)
        elif sdf_mpsoc_sub and not res:
            return (True, None)
        else:
            return (False, None)

    def covered_vertexes(self):
        yield from self.wcet_vertexes
        yield from self.token_wcct_vertexes
        yield from self.goals_vertexes
        yield from self.sdf_mpsoc_sub.covered_vertexes()

    def compute_deduced_properties(self):
        sdf_actors = self.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = self.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        vertex_expansions = self.sdf_mpsoc_sub.vertex_expansions
        units_enum = {
            **self.sdf_mpsoc_sub.cores_enum,
            **self.sdf_mpsoc_sub.comm_enum
        }
        expanded_units_enum = {
            **self.sdf_mpsoc_sub.expanded_cores_enum,
            **self.sdf_mpsoc_sub.expanded_comm_enum,
        }
        cores_enum = self.sdf_mpsoc_sub.cores_enum
        expanded_cores_enum = self.sdf_mpsoc_sub.expanded_cores_enum
        expanded_wcet = np.zeros(
            (
                len(sdf_actors),
                len(expanded_cores_enum)
            ),
            dtype=int
        )
        for (aidx, a) in enumerate(sdf_actors):
            for (e, eidx) in cores_enum.items():
                for ex in vertex_expansions[e]:
                    exidx = expanded_units_enum[ex]
                    expanded_wcet[aidx, exidx] = self.wcet[aidx, eidx]
        expanded_token_wcct = np.zeros(
            (
                len(sdf_channels),
                len(expanded_units_enum)
            ),
            dtype=int
        )
        for (cidx, c) in enumerate(sdf_channels):
            for (e, eidx) in units_enum.items():
                for ex in vertex_expansions[e]:
                    exidx = expanded_units_enum[ex]
                    expanded_token_wcct[cidx, exidx] = self.token_wcct[cidx, eidx]
        self.expanded_wcet = expanded_wcet
        # self.expanded_wcct = expanded_wcct
        self.expanded_token_wcct = expanded_token_wcct

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def get_mzn_data(self):
        sdf_actors = self.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        cores = self.sdf_mpsoc_sub.cores
        data = self.sdf_mpsoc_sub.get_mzn_data()
        # remake the wcet and wcct with proper data
        data['max_steps'] = self.sdf_mpsoc_sub.max_steps
        data['wcet'] = self.expanded_wcet.tolist()
        data['token_wcct'] = self.expanded_token_wcct.tolist()
        data['objective_weights'] = [
            self.throughput_importance,
            self.latency_importance
        ]
        return data

    def rebuild_forsyde_model(self, results):
        return self.sdf_mpsoc_sub.rebuild_forsyde_model(results)


@dataclass
class SDFToMultiCoreCharacterizedJobs(DecisionModel, MinizincAble):

    # this partial identification is not dominated by self
    sdf_mpsoc_char_sub: SDFToMultiCoreCharacterized = SDFToMultiCoreCharacterized()

    # properties
    jobs: Set[Vertex] = field(default_factory=set)
    jobs_actors: Dict[Vertex, Tuple[Vertex, int]] = field(default_factory=dict)

    @classmethod
    def identify(cls, model, identified):
        res = None
        sdf_mpsoc_char_sub = next(
            (p for p in identified if isinstance(p, SDFToMultiCoreCharacterized)),
            None)
        if sdf_mpsoc_char_sub:
            sdf_actors = sdf_mpsoc_char_sub.\
                sdf_mpsoc_sub.\
                sdf_orders_sub.\
                sdf_exec_sub.sdf_actors
            sdf_repetition_vector = sdf_mpsoc_char_sub.\
                sdf_mpsoc_sub.\
                sdf_orders_sub.\
                sdf_exec_sub.sdf_repetition_vector
            jobs_actors = {
                Vertex(identifier=f"{a.identifier}_{i}"): (a, aidx)
                for (aidx, a) in enumerate(sdf_actors)
                for i in sdf_repetition_vector[aidx]
            }
            jobs = set(jobs_actors.keys())
            res = cls(
                sdf_mpsoc_char_sub=sdf_mpsoc_char_sub,
                jobs_actors=jobs_actors,
                jobs=jobs
            )
        if res:
            return (True, res)
        else:
            return (False, None)

    def covered_vertexes(self):
        yield from self.sdf_mpsoc_char_sub.covered_vertexes()

    def get_mzn_model_name(self):
        return "sdf_job_scheduling.mzn"

    def get_mzn_data(self):
        # use the non faked part of the covered problem
        # to save some code
        data = self.sdf_mpsoc_char_sub.get_mzn_data()
        data['jobs'] = set(int(i)+1 for (i, v) in enumerate(self.jobs))
        # data['activations'] = self['sdf_repetition_vector'][:, 0].tolist()
        data['jobs_actors'] = [
            int(aidx)+1 for (k, (actor, aidx)) in self.jobs_actors.items()
        ]
        # delete spurious elements
        # data.pop('max_steps')
        data.pop('activations')
        return data

    def rebuild_forsyde_model(self, results):
        new_model = self.covered_model()
        return new_model


def _get_standard_problems() -> Set[Type[DecisionModel]]:
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
    identified: List[DecisionModel] = []
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
    concurrent_idents: int = os.cpu_count() or 1
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


def choose_decision_models(
    models: List[DecisionModel],
    criteria: ChoiceCriteria = ChoiceCriteria.DOMINANCE,
    desired_names: List[str] = []
) -> List[DecisionModel]:
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
    concurrent_idents: int = os.cpu_count() or 1
) -> List[DecisionModel]:
    '''
    AsyncIO version of the same function. Wraps the non-async version.
    '''
    return identify_decision_models_parallel(
        model,
        problems,
        concurrent_idents
    )

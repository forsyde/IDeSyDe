from dataclasses import dataclass
from dataclasses import field
from typing import Tuple
from typing import Set
from typing import Dict
from typing import List

import numpy as np

import idesyde.sdf as sdfapi
from forsyde.io.python.api import ForSyDeModel
from forsyde.io.python.core import Vertex
from forsyde.io.python.core import Edge
from forsyde.io.python.core import Port
from forsyde.io.python.types import SDFCombType
from forsyde.io.python.types import ProcessType
from forsyde.io.python.types import SignalType
from forsyde.io.python.types import AbstractOrderingType
from forsyde.io.python.types import MappingType
from forsyde.io.python.types import SchedulingType
from forsyde.io.python.types import AbstractProcessingComponentType
from forsyde.io.python.types import AbstractCommunicationComponentType
from idesyde.identification.interfaces import DecisionModel
from idesyde.identification.interfaces import MinizincableDecisionModel


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

    sdf_actors: List[Vertex] = field(default_factory=list)
    sdf_delays: List[Vertex] = field(default_factory=list)
    sdf_channels: List[Tuple[Vertex, Vertex, List[Vertex]]] = field(default_factory=list)
    sdf_topology: np.ndarray = np.zeros((0, 0))
    sdf_repetition_vector: np.ndarray = np.zeros((0))
    sdf_initial_tokens: np.ndarray = np.zeros((0))
    sdf_pass: List[Vertex] = field(default_factory=list)

    sdf_max_tokens: np.ndarray = np.zeros((0))

    def covered_vertexes(self):
        yield from self.sdf_actors
        yield from self.sdf_channels

    def compute_deduced_properties(self):
        self.max_tokens = np.zeros((len(self.sdf_channels)), dtype=int)
        for (cidx, c) in enumerate(self.sdf_channels):
            self.max_tokens[cidx] = max(self.sdf_topology[cidx, aidx] * self.sdf_repetition_vector[aidx]
                                        for (aidx, a) in enumerate(self.sdf_actors))


@dataclass
class SDFToOrders(MinizincableDecisionModel):

    # sub identifications
    sdf_exec_sub: SDFExecution = SDFExecution()

    # partial identification
    orderings: List[Vertex] = field(default_factory=list)

    def covered_vertexes(self):
        yield from self.orderings
        yield from self.sdf_exec_sub.covered_vertexes()

    def compute_deduced_properties(self):
        self.orderings_enum = {k: i for (i, k) in enumerate(self.orderings)}

    def get_mzn_data(self):
        data = dict()
        sub = self.sdf_exec_sub
        data['sdf_actors'] = range(1, len(sub.sdf_actors) + 1)
        data['sdf_channels'] = range(1, len(sub.sdf_channels) + 1)
        data['sdf_topology'] = sub.sdf_topology.tolist()
        data['max_steps'] = len(sub.sdf_pass) // len(self.orderings)
        data['max_steps'] += 1 if len(sub.sdf_pass) % len(self.orderings) > 0 else 0
        data['max_tokens'] = sub.max_tokens.tolist()
        data['activations'] = sub.sdf_repetition_vector[:, 0].tolist()
        data['static_orders'] = range(1, len(self.orderings) + 1)
        # TODO: find a awya to compute the initial tokens
        # reliably
        data['initial_tokens'] = [0 for c in sub.sdf_channels]
        return data

    def get_mzn_model_name(self):
        return 'sdf_order_linear_dmodel.mzn'

    def rebuild_forsyde_model(self, results):
        return ForSyDeModel()


@dataclass
class SDFToMultiCore(MinizincableDecisionModel):

    # covered decision models
    sdf_orders_sub: SDFToOrders = SDFToOrders()

    # partially identified
    cores: List[Vertex] = field(default_factory=list)
    busses: List[Vertex] = field(default_factory=list)
    connections: List[Edge] = field(default_factory=list)

    # deduced properties
    vertex_expansions: Dict[Vertex, List[Vertex]] = field(default_factory=dict)
    edge_expansions: Dict[Edge, List[Edge]] = field(default_factory=dict)
    # cores_enum: Dict[Vertex, int] = field(default_factory=dict)
    # comm_enum: Dict[Vertex, int] = field(default_factory=dict)
    # expanded_cores_enum: Dict[Vertex, int] = field(default_factory=dict)
    expanded_enum: Dict[Vertex, int] = field(default_factory=dict)
    max_steps: int = 1

    def covered_vertexes(self):
        yield from self.cores
        yield from self.busses
        yield from self.sdf_orders_sub.covered_vertexes()

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def compute_deduced_properties(self):
        self.sdf_orders_sub.compute_deduced_properties()
        vertex_expansions = {p: [p] for p in self.cores}
        edge_expansions = {e: [] for e in self.connections}
        expanded_enum = {p: i for (i, p) in enumerate(self.cores)}
        # expand all TDMAs to their slot elements
        units_enum_index = len(expanded_enum)
        for (i, bus) in enumerate(self.busses):
            vertex_expansions[bus] = []
            for s in range(bus.properties['slots']):
                bus_slot = Vertex(identifier=f'{bus.identifier}_slot_{s}')
                expanded_enum[bus_slot] = units_enum_index
                vertex_expansions[bus].append(bus_slot)
                units_enum_index += 1
        firings = int(np.sum(self.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector))
        max_steps = firings // len(self.cores)
        if firings % len(self.cores) > 0:
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
                                expanded_e = Edge(source_vertex=v_new, target_vertex=o_new, edge_type=e.edge_type)
                                edge_expansions[e].add(expanded_e)
        self.vertex_expansions = vertex_expansions
        self.edge_expansions = edge_expansions
        self.expanded_enum = expanded_enum
        # self.expanded_comm_enum = expanded_comm_enum
        # self.cores_enum = cores_enum
        # self.comm_enum = comm_enum
        self.max_steps = max_steps

    def get_mzn_data(self):
        data = self.sdf_orders_sub.get_mzn_data()
        # expanded_units_enum = {**self.expanded_cores_enum, **self.expanded_comm_enum}
        data['procs'] = set(i + 1 for (p, i) in self.expanded_enum.items()
                            if p.is_type(AbstractProcessingComponentType))
        data['comm_units'] = set(i + 1 for (c, i) in self.expanded_enum.items()
                                 if c.is_type(AbstractCommunicationComponentType))
        data['units_neighs'] = [
            set(self.expanded_enum[ex.target_vertex] + 1 for (e, el) in self.edge_expansions.items() for ex in el
                if ex.source_vertex == u).union(
                    set(self.expanded_enum[ex.source_vertex] + 1 for (e, el) in self.edge_expansions.items()
                        for ex in el if ex.target_vertex == u)) for (u, uidx) in self.expanded_enum.items()
        ]
        # since the minizinc model requires wcet and wcct,
        # we fake it with almost unitary assumption
        data['wcet'] = (data['max_tokens'] * np.ones((len(data['sdf_actors']), len(self.cores)), dtype=int)).tolist()
        data['token_wcct'] = (np.ones((len(data['sdf_channels']), len(data['procs']) + len(data['comm_units'])),
                                      dtype=int)).tolist()
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
        sdf_actors = self.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = self.sdf_orders_sub.sdf_exec_sub.sdf_channels
        orderings = self.sdf_orders_sub.orderings
        for (pidx, core) in enumerate(self.cores):
            ordering = orderings[pidx]
            if not new_model.has_edge(core, ordering, key="object"):
                new_edge = Edge(source_vertex=core,
                                target_vertex=ordering,
                                source_vertex_port=Port(identifier="execution", port_type=AbstractOrderingType()),
                                edge_type=MappingType())
                new_model.add_edge(core, ordering, object=new_edge)
            slot = 0
            for t in range(max_steps):
                sdf_pass = sdfapi.get_PASS(
                    sdf_topology,
                    np.array([[results["mapped_actors"][a][pidx][t] for a in range(len(sdf_actors))]]).transpose(),
                    np.array([[results["buffer_start"][c][pidx][t] for c in range(len(sdf_channels))]]).transpose())
                for aidx in sdf_pass:
                    actor = sdf_actors[aidx]
                    if not new_model.has_edge(ordering, actor, key="object"):
                        new_edge = Edge(source_vertex=ordering,
                                        target_vertex=actor,
                                        source_vertex_port=Port(identifier=f"slot[{slot}]", port_type=ProcessType()),
                                        edge_type=SchedulingType())
                        new_model.add_edge(ordering, actor, object=new_edge)
                        slot += 1
        vertex_expansions = self.vertex_expansions
        expanded_enum = self.expanded_enum
        for (commidx, comm) in enumerate(self.busses):
            slot_expansions = vertex_expansions[comm]
            ordering = orderings[commidx + len(self.cores)]
            if not new_model.has_edge(comm, ordering, key="object"):
                new_edge = Edge(source_vertex=comm,
                                target_vertex=ordering,
                                source_vertex_port=Port(identifier="timeslots", port_type=AbstractOrderingType()),
                                edge_type=MappingType())
                new_model.add_edge(comm, ordering, object=new_edge)
            for (cidx, c) in enumerate(sdf_channels):
                for (slotidx, slot) in enumerate(slot_expansions):
                    unitidx = expanded_enum[slot]
                    if any((results['send_allocation'][cidx][p][pp][t][tt][unitidx] > 0
                            for (pidx, p) in enumerate(self.cores)
                            for (ppidx, pp) in enumerate(self.cores)
                            for t in range(max_steps)
                            for tt in range(max_steps)))\
                            and not new_model.has_edge(ordering, c, key="object"):
                        new_edge = Edge(source_vertex=ordering,
                                        target_vertex=c,
                                        source_vertex_port=Port(identifier=f"slot[{slotidx}]"),
                                        edge_type=SchedulingType())
                        new_model.add_edge(ordering, c, object=new_edge)
        return new_model


@dataclass
class SDFToMultiCoreCharacterized(MinizincableDecisionModel):

    # covered partial identifications
    sdf_mpsoc_sub: SDFToMultiCore = SDFToMultiCore()

    # elements that are partially identified
    wcet_vertexes: List[Vertex] = field(default_factory=list)
    token_wcct_vertexes: List[Vertex] = field(default_factory=list)
    goals_vertexes: List[Vertex] = field(default_factory=list)
    wcet: np.ndarray = np.array((0, 0), dtype=int)
    token_wcct: np.ndarray = np.zeros((0, 0))
    throughput_importance: int = 0
    latency_importance: int = 0
    send_overhead: np.ndarray = np.array((0, 0), dtype=int)
    read_overhead: np.ndarray = np.array((0, 0), dtype=int)

    # deduced properties
    expanded_wcet: np.ndarray = np.array((0, 0), dtype=int)
    expanded_token_wcct: np.ndarray = np.zeros((0, 0), dtype=int)

    def covered_vertexes(self):
        yield from self.wcet_vertexes
        yield from self.token_wcct_vertexes
        yield from self.goals_vertexes
        yield from self.sdf_mpsoc_sub.covered_vertexes()

    def compute_deduced_properties(self):
        sdf_actors = self.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = self.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        vertex_expansions = self.sdf_mpsoc_sub.vertex_expansions
        cores = self.sdf_mpsoc_sub.cores
        busses = self.sdf_mpsoc_sub.busses
        expanded_enum = self.sdf_mpsoc_sub.expanded_enum
        expanded_wcet = np.zeros((len(sdf_actors), sum(len(l) for (v, l) in vertex_expansions.items() if v in cores)),
                                 dtype=int)
        for (aidx, a) in enumerate(sdf_actors):
            for (pidx, p) in enumerate(cores):
                for ex in vertex_expansions[p]:
                    exidx = expanded_enum[ex]
                    expanded_wcet[aidx, exidx] = self.wcet[aidx, pidx]
        expanded_token_wcct = np.zeros(
            (len(sdf_channels), sum(len(l) for (v, l) in vertex_expansions.items() if v in busses)), dtype=int)
        for (cidx, c) in enumerate(sdf_channels):
            for (bidx, b) in enumerate(busses):
                for ex in vertex_expansions[b]:
                    exidx = expanded_enum[ex]
                    expanded_token_wcct[cidx, exidx] = self.token_wcct[cidx, bidx]
        self.expanded_wcet = expanded_wcet
        # self.expanded_wcct = expanded_wcct
        self.expanded_token_wcct = expanded_token_wcct

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def get_mzn_data(self):
        data = self.sdf_mpsoc_sub.get_mzn_data()
        # remake the wcet and wcct with proper data
        data['max_steps'] = self.sdf_mpsoc_sub.max_steps
        data['wcet'] = self.expanded_wcet.tolist()
        data['token_wcct'] = self.expanded_token_wcct.tolist()
        data['objective_weights'] = [self.throughput_importance, self.latency_importance]
        return data

    def rebuild_forsyde_model(self, results):
        return self.sdf_mpsoc_sub.rebuild_forsyde_model(results)


@dataclass
class SDFToMultiCoreCharacterizedJobs(MinizincableDecisionModel):

    # this partial identification is not dominated by self
    sdf_mpsoc_char_sub: SDFToMultiCoreCharacterized = SDFToMultiCoreCharacterized()

    # properties
    jobs: List[Vertex] = field(default_factory=list)
    jobs_actors: Dict[Vertex, Tuple[Vertex, int]] = field(default_factory=dict)

    def covered_vertexes(self):
        yield from self.sdf_mpsoc_char_sub.covered_vertexes()

    def get_mzn_model_name(self):
        return "sdf_job_scheduling.mzn"

    def get_mzn_data(self):
        # use the non faked part of the covered problem
        # to save some code
        data = self.sdf_mpsoc_char_sub.get_mzn_data()
        data['jobs'] = set(int(i) + 1 for (i, v) in enumerate(self.jobs))
        # data['activations'] = self['sdf_repetition_vector'][:, 0].tolist()
        data['jobs_actors'] = [int(aidx) + 1 for (k, (actor, aidx)) in self.jobs_actors.items()]
        # delete spurious elements
        data.pop('max_steps')
        data.pop('activations')
        return data

    def rebuild_forsyde_model(self, results):
        new_model = self.covered_model()
        return new_model
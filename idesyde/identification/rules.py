from typing import Generator, List, Sequence
from typing import Collection
from typing import Dict
from typing import Tuple
from typing import Optional
from typing import Iterator
from typing import cast
from typing import Set
from typing import Mapping
import itertools
import copy
from functools import reduce
import logging
import math
import operator

import networkx as nx  # type: ignore
import numpy as np
import sympy  # type: ignore

from forsyde.io.python.core import Vertex
from forsyde.io.python.core import ForSyDeModel
from forsyde.io.python.types import Function, SDFComb
from forsyde.io.python.types import SDFPrefix
from forsyde.io.python.types import Process
from forsyde.io.python.types import Signal
from forsyde.io.python.types import AbstractGrouping
from forsyde.io.python.types import AbstractProcessingComponent
from forsyde.io.python.types import InstrumentedProcessorTile
from forsyde.io.python.types import InstrumentedCommunicationInterconnect
from forsyde.io.python.types import InstrumentedFunction
from forsyde.io.python.types import InstrumentedSignal
from forsyde.io.python.types import LocationRequirement
from forsyde.io.python.types import AbstractCommunicationComponent
from forsyde.io.python.types import AbstractPhysicalComponent
from forsyde.io.python.types import WCET
from forsyde.io.python.types import WCCT
from forsyde.io.python.types import Goal
from forsyde.io.python.types import Output
from forsyde.io.python.types import MinimumThroughput
from forsyde.io.python.types import TimeDivisionMultiplexer
from forsyde.io.python.types import TimeTriggeredScheduler
from forsyde.io.python.types import AbstractMapping
from forsyde.io.python.types import AbstractScheduling

import idesyde.math as math_util
import idesyde.sdf as sdf_lib
from idesyde import LOGGER_NAME
from idesyde.identification.api import register_identification_rule
from idesyde.identification.interfaces import DecisionModel
from idesyde.identification.models import SDFExecution
from idesyde.identification.models import SDFToOrders
from idesyde.identification.models import SDFToMultiCore
from idesyde.identification.models import SDFToMultiCoreCharacterized
from idesyde.identification.models import TimeTriggeredPlatform
from idesyde.identification.models import JobScheduling
from idesyde.identification.models import JobType
from idesyde.identification.models import ProcType
from idesyde.identification.models import CommType

# class SDFAppRule(IdentificationRule):

_logger = logging.getLogger(LOGGER_NAME)

IdentificationOutput = Tuple[bool, Optional[DecisionModel]]


def _hash_sequence(li):
    v = 0
    for e in li:
        v = hash((v, e))
    return v


@register_identification_rule
def identify_sdf_app(model: ForSyDeModel, identified: List[DecisionModel]):
    """This Rule identifies (H)SDF applications that are consistent.

    To be consistent, the (H)SDF applications must:
        1. The topology matrix must have a null space of dimension 1,
            if there exists at least one channel in the application.
        2. There must be a PASS for the application.
    """
    result = None
    constructors: Sequence[SDFComb] = [c for c in model if isinstance(c, SDFComb)]
    delay_constructors: Sequence[SDFPrefix] = [c for c in model if isinstance(c, SDFPrefix)]
    # 1: find the actors
    sdf_actors: List[Vertex] = [a for c in constructors for a in model[c] if isinstance(a, Process)]
    # there are garanteed to be the correct vertexes by construction
    sdf_constructors = {a: c for a in sdf_actors for c in constructors if a in model[c]}
    sdf_impl = {a: i for a in sdf_actors for i in model[sdf_constructors[a]] if isinstance(i, InstrumentedFunction)}
    # 1: find the delays
    sdf_delays: List[Vertex] = [a for c in delay_constructors for a in model[c] if isinstance(a, Process)]
    # 1: get connected signals
    sdf_channels = {}
    # 1: check the model for the paths between actors
    for s in sdf_actors:
        for t in sdf_actors:
            if s != t:
                try:
                    # take away the source and target nodes
                    paths: Sequence[Sequence[Vertex]] = [path[1:-1] for path in nx.all_shortest_paths(model, s, t)]
                    # check if all elements in the path are signals or delays
                    paths_filtered: Sequence[Sequence[Vertex]] = [
                        path for path in paths if all(isinstance(v, Signal) or (v in sdf_delays) for v in path)
                    ]
                    if len(paths) > 0:
                        sdf_channels[(s, t)] = paths_filtered
                    # for path in nx.all_shortest_paths(model, s, t):
                    #     # take away the source and target nodes
                    #     path = path[1:-1]
                    #     # check if all elements in the path are signals or delays
                    #     if all(isinstance(v, Signal) or v in sdf_delays for v in path):
                    #         sdf_channels.append((s, t, path))
                except nx.exception.NetworkXNoPath:
                    pass
    # for (cidx, (s, t, _)) in enumerate(sdf_channels):
    #     for path in nx.all_shortest_paths(model, s, t):
    #         # take away the source and target nodes
    #         path = path[1:-1]
    #         # check if all elements in the path are signals or delays
    #         if all(isinstance(v, Signal) or v in sdf_delays for v in path):
    #             sdf_channels[cidx] = (s, t, path)
    # 1: remove all pre-built sdf channels that are empty
    # sdf_channels = [(s, t, e) for (s, t, e) in sdf_channels if e]
    # 1: remove all channels that are not Output-output only
    # sdf_channels = [(s, t, p) for (s, t, p) in sdf_channels if all(
    #     isinstance(e["object"], Output) for (_, e) in model[s][t].items())]
    # 2: define the initial tokens by counting the delays on every path
    initial_tokens = np.array(
        [sum(1 for d in sdf_channels[(s, t)] if d in sdf_delays) for (s, t) in sdf_channels], dtype=int
    )
    # 1: build the topology matrix
    sdf_topology = np.zeros((len(sdf_channels), len(sdf_actors)), dtype=int)
    for (cidx, (s, t)) in enumerate(sdf_channels):
        sidx = sdf_actors.index(s)
        tidx = sdf_actors.index(t)
        for path in sdf_channels[(s, t)]:
            # get the relevant port for the source and target actors
            # in this channel, assuming there is only one edge
            # connecting them
            out_port = next(v["object"].source_vertex_port for (k, v) in model[s][path[0]].items())
            in_port = next(v["object"].target_vertex_port for (k, v) in model[path[-1]][t].items())
            # get the constructor of the actors
            # look in their properties what is the production associated
            # with the channel, for the source...
            sdf_topology[cidx, sidx] += int(sdf_constructors[s].get_production()[out_port.identifier])
            sdf_topology[cidx, tidx] -= int(sdf_constructors[t].get_consumption()[in_port.identifier])
        # if out_port.identifier in s_constructor.get_production():
        # else:
        #     sdf_topology[cidx, sidx] = int(s_constructor.get_consumption()[out_port.identifier])
        # .. and for the target
        # if in_port.identifier in t_constructor.get_production():
        #     sdf_topology[cidx, tidx] = -int(s_constructor.get_production()[in_port.identifier])
        # else:
    # 1: calculate the null space
    null_space = sympy.Matrix(sdf_topology).nullspace()
    if len(null_space) == 1:
        # 2: transform the vector into the least integer multiple possible
        repetition_vector = math_util.integralize_vector(null_space[0])
        # 2: cast it to a numpy
        repetition_vector = np.array(repetition_vector, dtype=int)
        # 2: calculate a PASS!
        schedule = sdf_lib.get_PASS(sdf_topology, repetition_vector, initial_tokens)
        # and if it exists, create the model with the schedule
        if schedule != []:
            sdf_pass = [sdf_actors[idx] for idx in schedule]
            result = SDFExecution(
                sdf_actors=sdf_actors,
                sdf_constructors=cast(Dict[Vertex, Vertex], sdf_constructors),
                sdf_impl=cast(Dict[Vertex, Vertex], sdf_impl),
                sdf_channels=sdf_channels,
                sdf_topology=sdf_topology,
                sdf_repetition_vector=repetition_vector,
                sdf_initial_tokens=initial_tokens,
                sdf_pass=sdf_pass,
            )
    # conditions for fixpoints and partial identification
    if result:
        result.compute_deduced_properties()
        return (True, result)
    else:
        return (False, None)


# class SDFOrderRule(IdentificationRule):


@register_identification_rule
def identify_sdf_parallel(model: ForSyDeModel, identified: List[DecisionModel]):
    """This Rule Identifies possible parallel ordered schedules atop 'SDFExecution'.

    Since only the ordered schedules are considered, the "time" in the resulting
    model is still abstract.
    """
    res = None
    sdf_exec_sub = next((p for p in identified if isinstance(p, SDFExecution)), None)
    if sdf_exec_sub:
        orderings = [o for o in model if isinstance(o, TimeTriggeredScheduler)]
        if orderings:
            pre_scheduling = []
            for (a, o) in itertools.product(sdf_exec_sub.sdf_actors, orderings):
                for (ek, ed) in model.get_edge_data(o, a, default=dict()).items():
                    if isinstance(ed["object"], AbstractScheduling):
                        pre_scheduling.append(ed["object"])
            res = SDFToOrders(sdf_exec_sub=sdf_exec_sub, orderings=orderings, pre_scheduling=pre_scheduling)
    # conditions for fixpoints and partial identification
    if res:
        res.compute_deduced_properties()
        return (True, res)
    elif sdf_exec_sub and not res:
        return (True, None)
    else:
        return (False, None)


# class SDFToCoresRule(IdentificationRule):


@register_identification_rule
def identify_sdf_multi_core(model: ForSyDeModel, identified: List[DecisionModel]):
    """This 'IdentificationRule' identifies processing units atop 'SDFToOrders'

    The 'AbstractProcessingComponent' can communicate with each other
    if they are connected by one or more 'AbstractCommunicationComponent's.
    Otherwise, the processors are considered unreachable islands.

    It is assumed that the shortest path between two 'AbstractProcessingComponent'
    is _always_ the one chosen for data communication. Regardless on how
    the 'AbstractCommunicationComponent' communicates.
    """
    res = None
    sdf_orders_sub = next((p for p in identified if isinstance(p, SDFToOrders)), None)
    if sdf_orders_sub:
        cores = [p for p in model if isinstance(p, AbstractProcessingComponent)]
        comms = [p for p in model if isinstance(p, AbstractCommunicationComponent)]
        # find all cores that are connected between each other
        connections: Dict[Tuple[Vertex, Vertex], Collection[Collection[Vertex]]] = {}
        for (s, t) in itertools.product(cores, cores):
            if s != t:
                try:
                    connections[(s, t)] = [
                        path[1:-1]
                        for path in nx.all_shortest_paths(model, s, t)
                        if all(isinstance(v, AbstractCommunicationComponent) for v in path[1:-1])
                    ]
                except nx.exception.NetworkXNoPath:
                    pass
        # there must be orderings for both execution and communication
        comms_capacity = [0 for c in comms]
        for (i, c) in enumerate(comms):
            if isinstance(c, TimeDivisionMultiplexer):
                comms_capacity[i] = int(c.get_slots())
        if len(cores) + len(comms) <= len(sdf_orders_sub.orderings):
            # TODO: this pre mapping has nothing to do with the newest 2021-04-26 view of it.
            # pre_mapping = []
            # for ((oidx, o), (pidx, p)) in itertools.product(enumerate(sdf_orders_sub.orderings), enumerate(cores)):
            #     for (ek, ed) in model.get_edge_data(p, o, default=dict()).items():
            #         if isinstance(ed["object"], AbstractMapping):
            #             pre_mapping.append(ed["object"])
            res = SDFToMultiCore(
                sdf_orders_sub=sdf_orders_sub,
                cores=cores,
                comms=comms,
                connections=cast(Dict[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]], connections),
                comms_capacity=comms_capacity
                # pre_mapping=pre_mapping,
            )
    # conditions for fixpoints and partial identification
    if res:
        res.compute_deduced_properties()
        return (True, res)
    elif not res and sdf_orders_sub:
        return (True, None)
    else:
        return (False, None)


# class SDFToCoresCharacterizedRule(IdentificationRule):


@register_identification_rule
def identify_sdf_multi_core_instrumented(model: ForSyDeModel, identified: List[DecisionModel]):
    """This 'IdentificationRule' add WCET and WCCT atop 'SDFToCoresRule'"""
    res = None
    sdf_mpsoc_sub = next((p for p in identified if isinstance(p, SDFToMultiCore)), None)
    if sdf_mpsoc_sub:
        sdf_actors = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        cores = sdf_mpsoc_sub.cores
        comms = sdf_mpsoc_sub.comms
        # list(model.get_vertexes(WCET.get_instance()))
        wcet_vertexes = [w for w in model if isinstance(w, WCET)]
        # list(model.get_vertexes(WCCT.get_instance()))
        token_wcct_vertexes = [w for w in model if isinstance(w, WCCT)]
        wcet = np.zeros((len(sdf_actors), len(cores)), dtype=int)
        token_wcct = np.zeros((len(sdf_channels), len(comms)), dtype=int)
        for (aidx, a) in enumerate(sdf_actors):
            for (pidx, p) in enumerate(cores):
                wcet[aidx, pidx] = max(
                    (w.get_time() for w in wcet_vertexes if (a in model[w]) and (p in model[w])), default=1
                )
                # (int(w.properties['time'])
                #  for w in model.predecessors(a) if w in model.predecessors(p) and isinstance(w, WCET)),
                # default=0)
        # iterate through all elements of a channel and take the
        # maximum of the WCCTs for that path, since in a channels it is
        # expected that the data type is the same along the entire path
        for (cidx, (s, t)) in enumerate(sdf_channels):
            paths = sdf_channels[(s, t)]
            for (pidx, comm) in enumerate(comms):
                token_wcct[cidx, pidx] = sum(
                    int(w.get_time())
                    for path in paths
                    for e in path
                    for w in token_wcct_vertexes
                    if e in model[w] and comm in model[w]
                )
        # although there should be only one Th vertex
        # per application, we apply maximun just in case
        # someone forgot to make sure there is only one annotation
        # per application
        goals_vertexes = [v for v in model if isinstance(v, Goal)]
        throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
        throughput_importance = 0
        # check that all actors are covered by a throughput goal
        if all(sum(1 for p in nx.all_simple_paths(model, g, a)) > 0 for g in throughput_vertexes for a in sdf_actors):
            throughput_importance = max(
                (int(v.properties["apriori_importance"]) for v in throughput_vertexes), default=0
            )
        # if all wcets are valid, the model is considered characterized
        if 0 not in np.unique(wcet):
            res = SDFToMultiCoreCharacterized(
                sdf_mpsoc_sub=sdf_mpsoc_sub,
                wcet_vertexes=wcet_vertexes,
                token_wcct_vertexes=token_wcct_vertexes,
                wcet=wcet,
                token_wcct=token_wcct,
                throughput_importance=throughput_importance,
                latency_importance=0,
                goals_vertexes=goals_vertexes,
            )
    if res:
        res.compute_deduced_properties()
        return (True, res)
    elif sdf_mpsoc_sub and not res:
        return (True, None)
    else:
        return (False, None)


# class SDFMulticoreToJobsRule(IdentificationRule):


# @register_identification_rule
def identify_jobs_from_sdf_multicore(
    model: ForSyDeModel, identified: List[DecisionModel]
) -> Tuple[bool, Optional[DecisionModel]]:
    res = None
    sdf_mpsoc_char_sub: Optional[SDFToMultiCoreCharacterized] = next(
        (p for p in identified if isinstance(p, SDFToMultiCoreCharacterized)), None
    )
    if sdf_mpsoc_char_sub:
        sdf_actors = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        jobs, weak_next, strong_next = sdf_lib.sdf_to_jobs(
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_topology,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_initial_tokens,
        )
        cores = sdf_mpsoc_char_sub.sdf_mpsoc_sub.cores
        orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings
        procs: List[Sequence[Vertex]] = [
            [p, o] for (p, o) in itertools.product(cores, orderings) if model.has_edge(p, o) or model.has_edge(o, p)
        ]
        for (p, o) in zip(cores, orderings):
            if not any(p in li or o in li for li in procs):
                procs.append([p, o])
        proc_capacity: Dict[ProcType, int] = {}
        # one ordering per comm
        comms: List[Sequence[Vertex]] = [
            [c, o]
            for (c, o) in itertools.product(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms, reversed(orderings))
            if model.has_edge(c, o) or model.has_edge(o, c)
        ]
        for (comm, order) in zip(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms, reversed(orderings)):
            if not any(comm in li or order in li for li in comms):
                comms.append([comm, order])
        comm_capacity: Dict[CommType, int] = {
            i: max(c.get_slots() if isinstance(c, TimeDivisionMultiplexer) else 0 for c in li)
            for (i, li) in enumerate(comms)
        }
        # fetch the wccts and wcets
        wcet: Dict[Tuple[JobType, ProcType], int] = {}
        wcct: Dict[Tuple[JobType, JobType, CommType], int] = {}
        for (j, job) in jobs:
            # for every job, which is a repetition of an actor a, build up ther wcet
            a = sdf_actors.index(job)
            for (i, p) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.cores):
                wcet[((j, job), i)] = sdf_mpsoc_char_sub.wcet[a, i]
            # now iterate through every:
            #  other job, with the corresponding
            #  channel between then and,
            #  every comm element in the pat between these jobs,
            # and build up the additive WCCT in such communication path.
            for (jj, jjob) in jobs:
                for (cidx, (s, t)) in enumerate(sdf_channels):
                    if s == job and t == jjob:
                        for (i, _) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms):
                            wcct[((j, job), (jj, jjob), i)] = sdf_mpsoc_char_sub.token_wcct[cidx, i]
        orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings
        pre_scheduling: Dict[JobType, int] = {}
        pre_mapping: Dict[JobType, ProcType] = {}
        for (i, actor) in jobs:
            # go through the trigger times to fetch when the actor might be
            # activated
            connected_orderings: Generator[TimeTriggeredScheduler, None, None] = (
                o for o in orderings if model.has_edge(o, a) and actor.identifier in o.get_trigger_time().values()
            )
            triggers: Collection[int] = sorted(
                (
                    int(k)
                    for (k, v) in o.get_trigger_time().items()
                    if v == actor.identifier
                    for o in connected_orderings
                )
            )
            for (i, time) in enumerate(triggers):
                # pass all the timings to the job
                pre_scheduling[(i, actor)] = time
                # check if the time triggered scheduled belong to a machine already
                for (pidx, proc) in enumerate(procs):
                    if all(nx.has_path(model, component, actor) for component in proc):
                        pre_mapping[(i, actor)] = pidx
        comm_channels = {
            ((i1, actor1), (i1, actor2)): sdf_channels[(actor1, actor2)]
            for ((i1, actor1), (i2, actor2)) in itertools.product(jobs, jobs)
            if (actor1, actor2) in sdf_channels
        }
        res = JobScheduling(
            abstracted_vertexes=cast(
                Sequence[Vertex],
                set(sdf_mpsoc_char_sub.covered_vertexes())
                | set(sdf_mpsoc_char_sub.wcet_vertexes)
                | set(sdf_mpsoc_char_sub.token_wcct_vertexes),
            ),
            comms=comms,
            procs=procs,
            jobs=jobs,
            comm_channels=comm_channels,
            weak_next=cast(Dict[JobType, Sequence[JobType]], weak_next),
            strong_next=cast(Dict[JobType, Sequence[JobType]], strong_next),
            paths=sdf_mpsoc_char_sub.sdf_mpsoc_sub.connections,
            pre_mapping=pre_mapping,
            pre_scheduling=pre_scheduling,
            goals_vertexes=sdf_mpsoc_char_sub.goals_vertexes,
            objective_weights=[sdf_mpsoc_char_sub.throughput_importance, sdf_mpsoc_char_sub.latency_importance],
            comm_capacity=comm_capacity,
            proc_capacity=proc_capacity,
            wcet=wcet,
            wcct=wcct,
        )
    if res:
        return (True, res)
    else:
        return (False, None)


@register_identification_rule
def identify_jobs_from_multi_sdf(
    model: ForSyDeModel, identified: List[DecisionModel]
) -> Tuple[bool, Optional[DecisionModel]]:
    sdf_multicore_sub: Optional[SDFToMultiCore] = next((p for p in identified if isinstance(p, SDFToMultiCore)), None)
    # early exit
    if not sdf_multicore_sub:
        return (False, None)
    sdf_actors = sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
    sdf_channels = sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
    jobs, weak_next, strong_next = sdf_lib.sdf_to_jobs(
        sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors,
        sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels,
        sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_topology,
        sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector,
        sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_initial_tokens,
    )
    cores = sdf_multicore_sub.cores
    orderings = sdf_multicore_sub.sdf_orders_sub.orderings
    procs: List[Sequence[Vertex]] = [
        [p, o]
        for (p, o) in itertools.product(sdf_multicore_sub.cores, orderings)
        if model.has_edge(p, o) or model.has_edge(o, p)
    ]
    for (p, o) in zip(cores, orderings):
        if not any(p in li or o in li for li in procs):
            procs.append([p, o])
    # one ordering per comm
    proc_capacity: Dict[ProcType, int] = {}
    comms: List[Sequence[Vertex]] = [
        [c, o]
        for (c, o) in itertools.product(sdf_multicore_sub.comms, reversed(orderings))
        if model.has_edge(c, o) or model.has_edge(o, c)
    ]
    # built the abstract comm for all comms that have not been assigned yet
    for (c, o) in itertools.product(sdf_multicore_sub.comms, reversed(orderings)):
        # check if any of the elements are already in the abstract communicator
        if not any(c in li or o in li for li in comms):
            comms.append([c, o])
    comm_capacity: Dict[CommType, int] = {}
    comms_key = {i: _hash_sequence(p) for (i, p) in enumerate(comms)}
    for (cidx, comm) in enumerate(comms):
        cap = min((c.get_slots() for c in comm if isinstance(c, TimeDivisionMultiplexer)), default=0)
        ckey = comms_key[cidx]
        comm_capacity[ckey] = cap
    pre_scheduling: Dict[JobType, int] = {}
    pre_mapping: Dict[JobType, ProcType] = {}
    for a in sdf_actors:
        for (pidx, proc) in enumerate(procs):
            # try to find if there's a path between the job and the
            # processors. Assume every edge is a different mapping
            core = next(u for u in proc if isinstance(u, AbstractProcessingComponent))
            # use shortest path algorithms since we want a clear path without any
            # repetitions or cycles
            paths = []
            try:
                # build a list out of it so the generator is consumed
                paths = list(nx.all_shortest_paths(model, core, a))
            except nx.exception.NetworkXNoPath:
                pass
            # can only have one actor, the one in the loop
            paths = [path for path in paths if sum(1 for u in path if isinstance(u, Process)) == 1]
            # cn only have one core, the one in the loop
            paths = [path for path in paths if sum(1 for u in path if isinstance(u, AbstractProcessingComponent)) == 1]
            # count the remaining paths now
            hits = sum(1 for _ in paths)
            for i in range(hits):
                pre_mapping[(i, a)] = pidx
        # go through the trigger times to fetch when the actor might be
        # activated
        connected_orderings: Iterator[TimeTriggeredScheduler] = (
            o for o in orderings if model.has_edge(o, a) and a.identifier in o.get_trigger_time().values()
        )
        triggers: Collection[int] = sorted(
            (int(k) for (k, v) in o.get_trigger_time().items() if v == a.identifier for o in connected_orderings)
        )
        for (i, time) in enumerate(triggers):
            # pass all the timings to the job
            pre_scheduling[(i, a)] = time
            # check if the time triggered scheduled belong to a machine already
            # for (p, proc) in enumerate(procs):
            #     if all(nx.has_path(model, component, a) for component in proc):
            #         pre_mapping[start_index + i] = p
    goals_vertexes = [v for v in model if isinstance(v, Goal)]
    throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
    throughput_importance = 0
    latency_importance = 0
    # check that all actors are covered by a throughput goal
    if all(nx.has_path(model, g, a) for g in throughput_vertexes for a in sdf_actors):
        throughput_importance = max((int(v.properties["apriori_importance"]) for v in throughput_vertexes), default=0)
    comm_channels = {
        ((jsi, js), (jti, jt)): sdf_channels[(js, jt)]
        for (jsi, js) in jobs
        for (jti, jt) in jobs
        if (js, jt) in sdf_channels
    }
    res = JobScheduling(
        abstracted_vertexes=set(sdf_multicore_sub.covered_vertexes()),
        comms=comms,
        procs=procs,
        jobs=jobs,
        comm_channels=comm_channels,
        weak_next=cast(Dict[JobType, Sequence[JobType]], weak_next),
        strong_next=cast(Dict[JobType, Sequence[JobType]], strong_next),
        paths=sdf_multicore_sub.connections,
        pre_mapping=pre_mapping,
        pre_scheduling=pre_scheduling,
        goals_vertexes=goals_vertexes,
        objective_weights=[throughput_importance, latency_importance],
        proc_capacity=proc_capacity,
        wcet={},
        wcct={},
    )
    return (True, res)
    # if res:
    #     return (True, res)
    # elif sdf_multicore_sub and not res:
    #     return (True, None)
    # else:
    #     return (False, None)


@register_identification_rule
def identify_instrumentation_in_jobs_from_sdf(model: ForSyDeModel, identified: List[DecisionModel]):
    res = None
    sub_jobs: Optional[JobScheduling] = next((p for p in identified if isinstance(p, JobScheduling)), None)
    sub_sdf: Optional[SDFExecution] = next((p for p in identified if isinstance(p, SDFExecution)), None)
    if sub_jobs and sub_sdf:
        time_scale = 1
        instrumented_procs: List[InstrumentedProcessorTile] = [
            p for (pdix, procs) in enumerate(sub_jobs.procs) for p in procs if isinstance(p, InstrumentedProcessorTile)
        ]
        jobs_instrumented = all(
            a in sub_sdf.sdf_impl and isinstance(sub_sdf.sdf_impl[a], InstrumentedFunction) for (_, a) in sub_jobs.jobs
        )
        if jobs_instrumented and len(instrumented_procs) == len(sub_jobs.procs):
            worst_cycles = np.zeros((len(sub_jobs.jobs), len(instrumented_procs)), dtype=np.uint64)
            for ((jidx, (i, a)), (pidx, p)) in itertools.product(
                enumerate(sub_jobs.jobs), enumerate(instrumented_procs)
            ):
                impl = cast(InstrumentedFunction, sub_sdf.sdf_impl[a])
                worst_cycles[jidx, pidx] += p.get_clock_cycles_per_float_op() * impl.get_max_float_operations()
                worst_cycles[jidx, pidx] += p.get_clock_cycles_per_integer_op() * impl.get_max_int_operations()
                worst_cycles[jidx, pidx] += p.get_clock_cycles_per_boolean_op() * impl.get_max_boolean_operations()
                # wcet[jidx, pidx] = math.ceil(float(wcet[jidx, pidx]) / float(p.get_min_frequency_hz()))
            frequency_matrix = np.zeros((len(sub_jobs.jobs), len(instrumented_procs)), dtype=np.uint64)
            for ((jidx, j), (pidx, p)) in itertools.product(enumerate(sub_jobs.jobs), enumerate(instrumented_procs)):
                frequency_matrix[jidx, pidx] = p.get_min_frequency_hz()
            # scale up until it
            min_clocks = np.amin(worst_cycles[worst_cycles > 0])
            max_freq = np.amax(frequency_matrix[frequency_matrix > 0])
            while (time_scale * min_clocks) // max_freq == 0:
                time_scale *= 1000
            wcet = ((time_scale * worst_cycles) / frequency_matrix).astype(np.uint64)
            wcct = np.zeros((len(sub_jobs.jobs), len(sub_jobs.jobs), len(sub_jobs.comms)), dtype=np.uint64)
            for ((jidx, actorj), (jjidx, actorjj), (cidx, comm)) in itertools.product(
                sub_jobs.jobs, sub_jobs.jobs, enumerate(sub_jobs.comms)
            ):
                if (actorj, actorjj) in sub_sdf.sdf_channels:
                    paths = sub_sdf.sdf_channels[(actorj, actorjj)]
                    # add up all the signals in a channel, for every commuication element in the
                    # connection path
                    wcct[jidx, jjidx, cidx] = sum(
                        math.ceil(
                            float(
                                sum(
                                    c.get_max_elem_size_bytes() * c.get_max_elem_count()
                                    for path in paths
                                    for c in path
                                    if isinstance(c, InstrumentedSignal)
                                )
                            )
                            / float(u.get_max_bandwith_bytes_per_sec())
                        )
                        for u in comm
                        if isinstance(u, InstrumentedCommunicationInterconnect)
                    )
                    wcct = (np.ceil(wcct / time_scale)).astype(np.uint64)
            # make an new decision model by shallow copy
            wcet_dict = {
                (j, pidx): int(wcet[jidx, pidx])
                for (jidx, j) in enumerate(sub_jobs.jobs)
                for (pidx, p) in enumerate(sub_jobs.procs)
            }
            wcct_dict = {
                (j, jj, cidx): int(wcct[jidx, jjidx, cidx])
                for (jidx, j) in enumerate(sub_jobs.jobs)
                for (jjdix, jj) in enumerate(sub_jobs.jobs)
                for (cidx, comm) in enumerate(sub_jobs.comms)
            }
            res = sub_jobs.new(wcet=wcet_dict, wcct=wcct_dict, time_scale=time_scale)
    if res:
        return (True, res)
    elif sub_jobs and sub_sdf and not res:
        return (True, None)
    else:
        return (False, None)


@register_identification_rule
def identify_location_req_in_jobs_from_sdf(
    model: ForSyDeModel, identified: List[DecisionModel]
) -> IdentificationOutput:
    sub_jobs: Sequence[JobScheduling] = [p for p in identified if isinstance(p, JobScheduling)]
    location_vertexes = [li for li in model if isinstance(li, LocationRequirement)]
    # check if any of the sub problems already covered the requirements
    if any(all(lver in sub.covered_vertexes() for lver in location_vertexes) for sub in sub_jobs):
        return (True, None)
    for sub in sub_jobs:
        location_req: Dict[JobType, Collection[int]] = dict()
        located_jobs = {l: [(i, j) for (i, j) in sub.jobs if j in model[l]] for l in location_vertexes}
        located_procs = {
            l: [(i, p) for (i, p) in enumerate(sub.procs) if any(u in model[l] for u in p)] for l in location_vertexes
        }
        for l in location_vertexes:
            jobs = located_jobs[l]
            procs = located_procs[l]
            for (i, j) in jobs:
                location_req[(i, j)] = [pi for (pi, p) in procs]
        res = sub.new(
            abstracted_vertexes=set(sub.covered_vertexes()) | set(location_vertexes),
            job_allowed_location=location_req,
        )
        if res not in identified:
            return (False, res)
    return (False, None)


@register_identification_rule
def identify_merge_job_scheduling_simple(
    model: ForSyDeModel, identified: List[DecisionModel]
) -> Tuple[bool, Optional[DecisionModel]]:
    sub_jobs: Collection[JobScheduling] = [p for p in identified if isinstance(p, JobScheduling)]
    if len(sub_jobs) <= 1:
        return (False, None)
    covered: Sequence[Vertex] = list(reduce(operator.or_, (set(m.covered_vertexes()) for m in identified), set()))
    jobs: Sequence[JobType] = list(reduce(operator.or_, (set(sub.jobs) for sub in sub_jobs), set()))
    procs: List[Sequence[Vertex]] = []
    proc_capacity: Mapping[ProcType, int] = {}
    comm_capacity: Mapping[CommType, int] = {}
    comms: List[Sequence[Vertex]] = []
    comm_channels: Dict[Tuple[JobType, JobType], Sequence[Sequence[Vertex]]] = {}
    weak_next: Dict[JobType, Sequence[JobType]] = {}
    strong_next: Dict[JobType, Sequence[JobType]] = {}
    paths: Dict[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]] = {}
    # mapping and location require special treatment since
    # the indexes of the virtual machines might the same
    # but htey still might be different machines altogther.
    # Therefore we use this `_hash_sequence` function
    # to make equally oredered lists be equal in a hash sense.
    # Later we rescue the indexes easily
    pre_mapping_proctype = {}
    location_req_proctype: Dict[JobType, Sequence[ProcType]] = {}
    job_capacity_req_proctype: Mapping[Tuple[JobType, ProcType], int] = {}
    pre_scheduling: Dict[JobType, int] = {}
    goals_vertexes: Sequence[Vertex] = set()
    objective_weights: Sequence[int] = []
    wcet: Dict[Tuple[JobType, ProcType], int] = {}
    wcct: Dict[Tuple[JobType, JobType, ProcType], int] = {}
    for sub in sub_jobs:
        procs_keys = set(_hash_sequence(k) for k in procs)
        procs += [proc for proc in sub.procs if _hash_sequence(proc) not in procs_keys]
        comms_keys = set(_hash_sequence(k) for k in comms)
        comms += [comm for comm in sub.comms if _hash_sequence(comm) not in comms_keys]
        for ((j, p), val) in sub.wcet.items():
            pkey = _hash_sequence(sub.procs[p])
            if (j, pkey) in wcet:
                wcet[(j, pkey)] = max(wcet[(j, pkey)], val)
            else:
                wcet[(j, pkey)] = val
        for ((j, jj, p), val) in sub.wcct.items():
            pkey = _hash_sequence(sub.procs[p])
            if (j, pkey) in wcct:
                wcct[(j, jj, pkey)] = max(wcct[(j, jj, pkey)], val)
            else:
                wcct[(j, jj, pkey)] = val
        for ((j1, j2), ch) in sub.comm_channels.items():
            if (j1, j2) in comm_channels:
                comm_keys = set(_hash_sequence(li) for li in comm_channels[(j1, j2)])
                comm_channels[(j1, j2)] += [li for li in ch if _hash_sequence(li) not in comm_keys]
            else:
                comm_channels[(j1, j2)] = list(ch)
        for ((v1, v2), ch) in sub.paths.items():
            if (v1, v2) in paths:
                paths_keys = set(_hash_sequence(li) for li in paths[(v1, v2)])
                paths[(v1, v2)] += [li for li in ch if _hash_sequence(li) not in paths_keys]
            else:
                paths[(v1, v2)] = list(ch)
        for (j, actor) in sub.jobs:
            if (j, actor) in weak_next:
                weak_next[(j, actor)] = list(set(weak_next[(j, actor)]) | set(sub.weak_next[(j, actor)]))
            else:
                weak_next[(j, actor)] = list(sub.weak_next[(j, actor)])
            if (j, actor) in strong_next:
                strong_next[(j, actor)] = list(set(strong_next[(j, actor)]) | set(sub.strong_next[(j, actor)]))
            else:
                strong_next[(j, actor)] = list(sub.strong_next[(j, actor)])
        for (j, p) in sub.pre_mapping.items():
            if j not in pre_mapping_proctype:
                pre_mapping_proctype[j] = _hash_sequence(sub.procs[p])
        for (j, ps) in sub.job_allowed_location.items():
            if j not in location_req_proctype:
                location_req_proctype[j] = {_hash_sequence(sub.procs[pidx]) for pidx in ps}
        for (pidx, v) in sub.proc_capacity.items():
            key = _hash_sequence(sub.procs[pidx])
            proc_capacity[key] = max(v, proc_capacity.get(key, 0))
        for (p, v) in sub.comm_capacity.items():
            key = _hash_sequence(sub.comms[p])
            comm_capacity[key] = max(v, comm_capacity.get(key, 0))
        for ((j, proc), cap) in sub.job_capacity_req.items():
            key = (j, _hash_sequence(sub.procs[proc]))
            job_capacity_req_proctype[key] = max(job_capacity_req_proctype.get(key, 0), cap)
        goals_vertexes = goals_vertexes.union(set(sub.goals_vertexes))
        # We kee padding objective weights if new list elements are found
        # since it is reasonable to assume that a model has the same amount
        # of global objectives altogether, all given in the same order
        if len(sub.objective_weights) > len(objective_weights):
            objective_weights += sub.objective_weights[len(objective_weights) :]
    # revert back the mapping  and location from hashes to indexes
    procs_inv = {_hash_sequence(p): pidx for (pidx, p) in enumerate(procs)}
    comms_inv = {_hash_sequence(p): pidx for (pidx, p) in enumerate(comms)}
    pre_mapping = {j: procs_inv[pre_mapping_proctype[j]] for j in jobs if j in pre_mapping_proctype}
    location_req = {j: set(procs_inv[p] for p in location_req_proctype[j]) for j in jobs if j in location_req_proctype}
    wcet = {(j, procs_inv[p]): v for ((j, p), v) in wcet.items()}
    wcct = {(j, jj, procs_inv[p]): v for ((j, jj, p), v) in wcct.items()}
    job_capacity_req = {(j, procs_inv[p]): cap for ((j, p), cap) in job_capacity_req_proctype.items()}
    proc_capacity = {procs_inv[k]: v for (k, v) in proc_capacity.items()}
    comm_capacity = {comms_inv[k]: v for (k, v) in comm_capacity.items()}
    # merge the worst cases by always tkaing the maximum in case of clash
    res = JobScheduling(
        abstracted_vertexes=reduce(operator.or_, (set(s.abstracted_vertexes) for s in sub_jobs), set()),
        jobs=jobs,
        procs=procs,
        comms=comms,
        comm_channels=comm_channels,
        weak_next=weak_next,
        strong_next=strong_next,
        paths=paths,
        pre_mapping=pre_mapping,
        pre_scheduling=pre_scheduling,
        goals_vertexes=goals_vertexes,
        objective_weights=objective_weights,
        wcet=wcet,
        wcct=wcct,
        time_scale=max(s.time_scale for s in sub_jobs),
        job_allowed_location=location_req,
        job_capacity_req=job_capacity_req,
        proc_capacity=proc_capacity,
        comm_capacity=comm_capacity
    )
    if res in identified and all(v in covered for v in model.nodes):
        return (True, None)
    if res in identified and not all(v in covered for v in model.nodes):
        return (False, None)
    else:
        return (False, res)


@register_identification_rule
def identify_time_triggered_platform(
    model: ForSyDeModel, identified: Collection[DecisionModel]
) -> IdentificationOutput:
    schedulers = [o for o in model if isinstance(o, TimeTriggeredScheduler)]
    cores = [p for p in model if isinstance(p, AbstractProcessingComponent)]
    comms = [p for p in model if isinstance(p, AbstractCommunicationComponent)]
    # find all cores that are connected between each other
    path: Dict[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]] = {}
    # check if there are enough schedulers
    if len(schedulers) < len(cores) + len(comms):
        return (True, None)
    for (s, t) in itertools.product(cores, cores):
        if s != t:
            try:
                path[(s, t)] = [
                    path[1:-1] for path in nx.all_shortest_paths(model, s, t) if all(v in comms for v in path[1:-1])
                ]
            except nx.exception.NetworkXNoPath:
                pass
    # there must be orderings for both execution and communication
    comms_bandwidth = {
        c: c.get_max_bandwith_bytes_per_sec() if isinstance(c, InstrumentedCommunicationInterconnect) else 0
        for c in comms
    }
    core_memory = {
        p: p.get_max_memory_internal_bytes() if isinstance(p, InstrumentedProcessorTile) else 0 for p in cores
    }
    # TODO: Identify also connected memory elements!
    core_scheduler = {p: o for p in cores for o in schedulers if o in model[p]}
    comm_scheduler = {p: o for p in comms for o in schedulers if o in model[p]}
    # second pass to now put all free schedulers together
    for (o, core) in itertools.product(schedulers, cores):
        if core not in core_scheduler and o not in core_scheduler.values():
            core_scheduler[core] = o
    for (o, comm) in itertools.product(reversed(schedulers), comms):
        if comm not in comm_scheduler and o not in comm_scheduler.values():
            comm_scheduler[comm] = o
    abstracted_vertexes = cast(Sequence[Vertex], set(schedulers) | set(cores) | set(comms))
    res = TimeTriggeredPlatform(
        schedulers=schedulers,
        comms_bandwidth=comms_bandwidth,
        core_memory=core_memory,
        core_scheduler=core_scheduler,
        comm_scheduler=comm_scheduler,
        paths=path,
        abstracted_vertexes=abstracted_vertexes,
    )
    return (True, res)


# class SDFToCoresRule(IdentificationRule):


@register_identification_rule
def identify_jobs_sdf_time_trigger_multicore(model: ForSyDeModel, identified: List[DecisionModel]):
    """This 'IdentificationRule' identifies processing units

    The 'AbstractProcessingComponent' can communicate with each other
    if they are connected by one or more 'AbstractCommunicationComponent's.
    Otherwise, the processors are considered unreachable islands.

    It is assumed that the shortest path between two 'AbstractProcessingComponent'
    is _always_ the one chosen for data communication. Regardless on how
    the 'AbstractCommunicationComponent' communicates.
    """
    sdf_app_sub = next((p for p in identified if isinstance(p, SDFExecution)), None)
    time_trig_platform_sub = next((p for p in identified if isinstance(p, TimeTriggeredPlatform)), None)
    # all things to be identified are already there but not dependent model
    # if not sdf_app_sub and not time_trig_platform_sub:
    #     return (True, None)
    # still waiting for dependent models
    if not sdf_app_sub or not time_trig_platform_sub:
        return (False, None)
    jobs, weak_next, strong_next = sdf_lib.sdf_to_jobs(
        sdf_app_sub.sdf_actors,
        sdf_app_sub.sdf_channels,
        sdf_app_sub.sdf_topology,
        sdf_app_sub.sdf_repetition_vector,
        sdf_app_sub.sdf_initial_tokens,
    )
    goals_vertexes = [v for v in model if isinstance(v, Goal)]
    throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
    throughput_importance = 0
    latency_importance = 0
    # check that all actors are covered by a throughput goal
    if all(nx.has_path(model, g, a) for g in throughput_vertexes for a in sdf_app_sub.sdf_actors):
        throughput_importance = max((int(v.properties["apriori_importance"]) for v in throughput_vertexes), default=0)
    comm_channels = {
        ((jsi, js), (jti, jt)): sdf_app_sub.sdf_channels[(js, jt)]
        for (jsi, js) in jobs
        for (jti, jt) in jobs
        if (js, jt) in sdf_app_sub.sdf_channels
    }
    job_capacity_req: Mapping[Tuple[JobType, ProcType], int] = {}
    for (jidx, (j, a)) in enumerate(jobs):
        impl = sdf_app_sub.sdf_impl.get(a, None)
        for (pidx, p) in enumerate(time_trig_platform_sub.core_scheduler):
            if isinstance(impl, InstrumentedFunction):
                job_capacity_req[(jidx, pidx)] = impl.get_max_memory_size_in_bytes()
    res = JobScheduling(
        abstracted_vertexes=set(sdf_app_sub.covered_vertexes())
        | set(time_trig_platform_sub.covered_vertexes())
        | set(goals_vertexes),
        jobs=jobs,
        comm_channels=comm_channels,
        weak_next=weak_next,
        strong_next=strong_next,
        proc_capacity={
            idx: time_trig_platform_sub.core_memory[proc] for (idx, proc) in enumerate(time_trig_platform_sub.core_scheduler)
        },
        comm_capacity={
            idx: time_trig_platform_sub.comms_bandwidth[p]
            for (idx, p) in enumerate(time_trig_platform_sub.comm_scheduler)
        },
        job_capacity_req=job_capacity_req,
        goals_vertexes=goals_vertexes,
        objective_weights=[throughput_importance, latency_importance],
        procs=[[p, o] for (p, o) in time_trig_platform_sub.core_scheduler.items()],
        comms=[[p, o] for (p, o) in time_trig_platform_sub.comm_scheduler.items()],
    )
    return (True, res)


@register_identification_rule
def identify_jobs_insturmentation_vertexes(
    model: ForSyDeModel, identified: List[DecisionModel]
) -> IdentificationOutput:
    sub_jobs: Sequence[JobScheduling] = [p for p in identified if isinstance(p, JobScheduling)]
    wcet_vertexes = set(li for li in model if isinstance(li, WCET))
    wcct_vertexes = set(li for li in model if isinstance(li, WCCT))
    abstracted = wcet_vertexes | wcct_vertexes
    # check if any of the sub problems already covered the requirements
    if any(all(v in sub.covered_vertexes() for v in abstracted) for sub in sub_jobs):
        return (True, None)
    for sub in sub_jobs:
        wcet: Dict[Tuple[JobType, ProcType], int] = dict()
        wcct: Dict[Tuple[JobType, JobType, CommType], int] = dict()
        for ((i, job), (pidx, p)) in itertools.product(sub.jobs, enumerate(sub.procs)):
            core = next(u for u in p if isinstance(u, AbstractProcessingComponent))
            # get the maximum value of all the WCET relationships between the job and the processing unit
            wcet[((i, job), pidx)] = max(
                int(w.get_time()) for w in wcet_vertexes if core in model[w] and job in model[w]
            )
        for ((((i, job), (j, job2)), channels), (pidx, p)) in itertools.product(
            sub.comm_channels.items(), enumerate(sub.comms)
        ):
            comm = next(u for u in p if isinstance(u, AbstractCommunicationComponent))
            # get the sum of all maximum values between channels and the communication units
            wcct[((i, job), (j, job2), pidx)] = sum(
                max(int(w.get_time()) for w in wcct_vertexes if comm in model[w] and c in model[w])
                for channel in channels
                for c in channel
            )
        res = sub.new(abstracted_vertexes=set(sub.covered_vertexes()) | set(abstracted), wcet=wcet, wcct=wcct)
        if res not in identified:
            return (False, res)
    return (False, None)

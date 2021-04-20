from typing import List
from typing import Collection
from typing import Dict
from typing import Tuple
from typing import Optional
from typing import Iterator
import itertools
import functools
import logging
import math

import networkx as nx
import numpy as np
import sympy

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
from idesyde.identification.models import JobType, SDFExecution
from idesyde.identification.models import SDFToOrders
from idesyde.identification.models import SDFToMultiCore
from idesyde.identification.models import SDFToMultiCoreCharacterized
from idesyde.identification.models import JobScheduling
from idesyde.identification.models import InstrumentedJobScheduling
from idesyde.identification.models import TimeTriggeredPlatform
from idesyde.identification.models import TimeTriggeredScheduler

# class SDFAppRule(IdentificationRule):

_logger = logging.getLogger(LOGGER_NAME)

IdentificationOutput = Tuple[bool, Optional[DecisionModel]]


@register_identification_rule
def identify_sdf_app(model: ForSyDeModel, identified: List[DecisionModel]):
    '''This Rule identifies (H)SDF applications that are consistent.

    To be consistent, the (H)SDF applications must:
        1. The topology matrix must have a null space of dimension 1,
            if there exists at least one channel in the application.
        2. There must be a PASS for the application.
    '''
    result = None
    constructors = [c for c in model if isinstance(c, SDFComb)]
    delay_constructors = [c for c in model if isinstance(c, SDFPrefix)]
    # 1: find the actors
    sdf_actors: List[Vertex] = [a for c in constructors for a in model[c] if isinstance(a, Process)]
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
                    paths = [path[1:-1] for path in nx.all_shortest_paths(model, s, t)]
                    # check if all elements in the path are signals or delays
                    paths = [path for path in paths if all(isinstance(v, Signal) or (v in sdf_delays) for v in path)]
                    if len(paths) > 0:
                        sdf_channels[(s, t)] = paths
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
    initial_tokens = np.array([sum(1 for d in sdf_channels[(s, t)] if d in sdf_delays) for (s, t) in sdf_channels],
                              dtype=int)
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
            result = SDFExecution(sdf_actors=sdf_actors,
                                  sdf_constructors=sdf_constructors,
                                  sdf_impl=sdf_impl,
                                  sdf_channels=sdf_channels,
                                  sdf_topology=sdf_topology,
                                  sdf_repetition_vector=repetition_vector,
                                  sdf_initial_tokens=initial_tokens,
                                  sdf_pass=sdf_pass)
    # conditions for fixpoints and partial identification
    if result:
        result.compute_deduced_properties()
        return (True, result)
    else:
        return (False, None)


#class SDFOrderRule(IdentificationRule):


@register_identification_rule
def identify_sdf_parallel(model: ForSyDeModel, identified: List[DecisionModel]):
    '''This Rule Identifies possible parallel ordered schedules atop 'SDFExecution'.

    Since only the ordered schedules are considered, the "time" in the resulting
    model is still abstract.
    '''
    res = None
    sdf_exec_sub = next((p for p in identified if isinstance(p, SDFExecution)), None)
    if sdf_exec_sub:
        orderings = [o for o in model if isinstance(o, TimeTriggeredScheduler)]
        if orderings:
            pre_scheduling = []
            for (a, o) in itertools.product(sdf_exec_sub.sdf_actors, orderings):
                for (ek, ed) in model.get_edge_data(o, a, default=dict()).items():
                    if isinstance(ed['object'], AbstractScheduling):
                        pre_scheduling.append(ed['object'])
            res = SDFToOrders(sdf_exec_sub=sdf_exec_sub, orderings=orderings, pre_scheduling=pre_scheduling)
    # conditions for fixpoints and partial identification
    if res:
        res.compute_deduced_properties()
        return (True, res)
    elif sdf_exec_sub and not res:
        return (True, None)
    else:
        return (False, None)


#class SDFToCoresRule(IdentificationRule):


@register_identification_rule
def identify_sdf_multi_core(model: ForSyDeModel, identified: List[DecisionModel]):
    '''This 'IdentificationRule' identifies processing units atop 'SDFToOrders'

    The 'AbstractProcessingComponent' can communicate with each other
    if they are connected by one or more 'AbstractCommunicationComponent's.
    Otherwise, the processors are considered unreachable islands.

    It is assumed that the shortest path between two 'AbstractProcessingComponent'
    is _always_ the one chosen for data communication. Regardless on how
    the 'AbstractCommunicationComponent' communicates.
    '''
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
                        path[1:-1] for path in nx.all_shortest_paths(model, s, t) if all(
                            isinstance(v, AbstractCommunicationComponent) for v in path[1:-1])
                    ]
                except nx.exception.NetworkXNoPath:
                    pass
        # there must be orderings for both execution and communication
        comms_capacity = [1 for c in comms]
        for (i, c) in enumerate(comms):
            if isinstance(c, TimeDivisionMultiplexer):
                comms_capacity[i] = int(c.get_slots())
        if len(cores) + len(comms) <= len(sdf_orders_sub.orderings):
            pre_mapping = []
            for (o, p) in itertools.product(sdf_orders_sub.orderings, cores):
                for (ek, ed) in model.get_edge_data(p, o, default=dict()).items():
                    if isinstance(ed['object'], AbstractMapping):
                        pre_mapping.append(ed['object'])
            res = SDFToMultiCore(sdf_orders_sub=sdf_orders_sub,
                                 cores=cores,
                                 comms=comms,
                                 connections=connections,
                                 comms_capacity=comms_capacity,
                                 pre_mapping=pre_mapping)
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
    '''This 'IdentificationRule' add WCET and WCCT atop 'SDFToCoresRule'
    '''
    res = None
    sdf_mpsoc_sub = next((p for p in identified if isinstance(p, SDFToMultiCore)), None)
    if sdf_mpsoc_sub:
        sdf_actors = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        cores = sdf_mpsoc_sub.cores
        comms = sdf_mpsoc_sub.comms
        # list(model.get_vertexes(WCET.get_instance()))
        wcet_vertexes = [w for w in model if isinstance(w, WCET)]
        token_wcct_vertexes = [w for w in model if isinstance(w, WCCT)]  # list(model.get_vertexes(WCCT.get_instance()))
        wcet = np.zeros((len(sdf_actors), len(cores)), dtype=int)
        token_wcct = np.zeros((len(sdf_channels), len(comms)), dtype=int)
        for (aidx, a) in enumerate(sdf_actors):
            for (pidx, p) in enumerate(cores):
                wcet[aidx, pidx] = max((w.get_time() for w in wcet_vertexes if (a in model[w]) and (p in model[w])),
                                       default=1)
                # (int(w.properties['time'])
                #  for w in model.predecessors(a) if w in model.predecessors(p) and isinstance(w, WCET)),
                # default=0)
        # iterate through all elements of a channel and take the
        # maximum of the WCCTs for that path, since in a channels it is
        # expected that the data type is the same along the entire path
        for (cidx, (s, t)) in enumerate(sdf_channels):
            paths = sdf_channels[(s, t)]
            for (pidx, p) in enumerate(comms):
                token_wcct[cidx, pidx] = sum(
                    int(w.get_time()) for path in paths for e in path for w in token_wcct_vertexes
                    if e in model[w] and p in model[w])
        # although there should be only one Th vertex
        # per application, we apply maximun just in case
        # someone forgot to make sure there is only one annotation
        # per application
        goals_vertexes = [v for v in model if isinstance(v, Goal)]
        throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
        throughput_importance = 0
        # check that all actors are covered by a throughput goal
        if all(sum(1 for p in nx.all_simple_paths(model, g, a)) > 0 for g in throughput_vertexes for a in sdf_actors):
            throughput_importance = max((int(v.properties['apriori_importance']) for v in throughput_vertexes),
                                        default=0)
        # if all wcets are valid, the model is considered characterized
        if 0 not in np.unique(wcet):
            res = SDFToMultiCoreCharacterized(sdf_mpsoc_sub=sdf_mpsoc_sub,
                                              wcet_vertexes=wcet_vertexes,
                                              token_wcct_vertexes=token_wcct_vertexes,
                                              wcet=wcet,
                                              token_wcct=token_wcct,
                                              throughput_importance=throughput_importance,
                                              latency_importance=0,
                                              goals_vertexes=goals_vertexes)
    if res:
        res.compute_deduced_properties()
        return (True, res)
    elif sdf_mpsoc_sub and not res:
        return (True, None)
    else:
        return (False, None)


#class SDFMulticoreToJobsRule(IdentificationRule):


#@register_identification_rule
def identify_jobs_from_sdf_multicore(model: ForSyDeModel,
                                     identified: List[DecisionModel]) -> Tuple[bool, Optional[DecisionModel]]:
    res = None
    sdf_mpsoc_char_sub: Optional[SDFToMultiCoreCharacterized] = next(
        (p for p in identified if isinstance(p, SDFToMultiCoreCharacterized)), None)
    if sdf_mpsoc_char_sub:
        sdf_actors = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        jobs, weak_next, strong_next = sdf_lib.sdf_to_jobs(
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_topology,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector,
            sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_initial_tokens)
        cores = sdf_mpsoc_char_sub.sdf_mpsoc_sub.cores
        orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings
        procs = []
        for (p, o) in itertools.product(cores, orderings):
            if model.has_edge(p, o):
                procs.append([p, o])
        for (p, o) in zip(cores, orderings):
            if not any(p == l[0] or o == l[1] for l in procs):
                procs.append([p, o])
        # one ordering per comm
        comms = []
        comm_capacity = []
        for (c, o) in itertools.product(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms, reversed(orderings)):
            if model.has_edge(c, o):
                comms.append([c, o])
        for (c, o) in zip(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms, reversed(orderings)):
            if not any(c == l[0] or o == l[1] for l in comms):
                comms.append([c, o])
        for l in comms:
            c = l[0]
            if isinstance(c, TimeDivisionMultiplexer):
                comm_capacity.append(c.get_slots())
            else:
                comm_capacity.append(1)
        # for (i, p) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms):
        #     orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings[i + len(procs)]
        #     comms.append([p, orderings])
        #     if isinstance(p, TimeDivisionMultiplexer):
        #         comm_capacity.append(p.get_slots())
        #     else:
        #         comm_capacity.append(1)
        # fetch the wccts and wcets
        wcet = np.zeros((len(jobs), len(procs)), dtype=int)
        wcct = np.zeros((len(jobs), len(jobs), len(comms)), dtype=int)
        for (j, job) in enumerate(jobs):
            # for every job, which is a repetition of an actor a, build up ther wcet
            a = sdf_actors.index(job)
            for (i, p) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.cores):
                wcet[j, i] = sdf_mpsoc_char_sub.wcet[a, i]
            # now iterate through every:
            #  other job, with the corresponding
            #  channel between then and,
            #  every comm element in the pat between these jobs,
            # and build up the additive WCCT in such communication path.
            for (jj, jjob) in enumerate(jobs):
                for (cidx, (s, t)) in enumerate(sdf_channels):
                    if s == job and t == jjob:
                        for (i, p) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms):
                            wcct[j, jj, i] = sdf_mpsoc_char_sub.token_wcct[cidx, i]
        orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings
        pre_scheduling = [0 for (j, _) in enumerate(jobs)]
        pre_mapping = [-1 for (j, _) in enumerate(jobs)]
        for a in sdf_actors:
            # go through the trigger times to fetch when the actor might be
            # activated
            connected_orderings: Iterator[TimeTriggeredScheduler] = (
                o for o in orderings if model.has_edge(o, a) and a.identifier in o.get_trigger_time().values())
            triggers: Collection[int] = sorted((int(k) for (k, v) in o.get_trigger_time().items() if v == a.identifier
                                                for o in connected_orderings))
            start_index = jobs.index(a)
            for (i, t) in enumerate(triggers):
                # pass all the timings to the job
                pre_scheduling[start_index + i] = t
                # check if the time triggered scheduled belong to a machine already
                for (p, proc) in enumerate(procs):
                    if all(nx.has_path(model, component, a) for component in proc):
                        pre_mapping[start_index + i] = p
        res = JobScheduling(
            abstracted=set(sdf_mpsoc_char_sub.covered_vertexes()) | set(sdf_mpsoc_char_sub.wcet_vertexes)
            | set(sdf_mpsoc_char_sub.token_wcct_vertexes),
            comms=comms,
            procs=procs,
            jobs=jobs,
            comm_jobs=sdf_channels,
            weak_next=weak_next,
            strong_next=strong_next,
            paths=sdf_mpsoc_char_sub.sdf_mpsoc_sub.connections,
            pre_mapping=pre_mapping,
            pre_scheduling=pre_scheduling,
            goals_vertexes=sdf_mpsoc_char_sub.goals_vertexes,
            objective_weights=[sdf_mpsoc_char_sub.throughput_importance, sdf_mpsoc_char_sub.latency_importance],
            comm_capacity=comm_capacity,
            # TODO: make this more general later
            # proc_capacity=[len(jobs) for _ in procs],
            wcet=wcet,
            wcct=wcct)
    if res:
        return (True, res)
    else:
        return (False, None)


@register_identification_rule
def identify_jobs_from_multi_sdf(model: ForSyDeModel,
                                 identified: List[DecisionModel]) -> Tuple[bool, Optional[DecisionModel]]:
    res = None
    sdf_multicore_sub: Optional[SDFToMultiCore] = next((p for p in identified if isinstance(p, SDFToMultiCore)), None)
    if sdf_multicore_sub:
        sdf_actors = sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
        sdf_channels = sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
        jobs, weak_next, strong_next = sdf_lib.sdf_to_jobs(
            sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors,
            sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels,
            sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_topology,
            sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector,
            sdf_multicore_sub.sdf_orders_sub.sdf_exec_sub.sdf_initial_tokens)
        cores = sdf_multicore_sub.cores
        orderings = sdf_multicore_sub.sdf_orders_sub.orderings
        procs = []
        for (p, o) in itertools.product(cores, orderings):
            if model.has_edge(p, o):
                procs.append([p, o])
        for (p, o) in zip(cores, orderings):
            if not any(p == l[0] or o == l[1] for l in procs):
                procs.append([p, o])
        # one ordering per comm
        comms = [[c, o] for (c, o) in itertools.product(sdf_multicore_sub.comms, reversed(orderings))
                 if model.has_edge(c, o) or model.has_edge(o, c)]
        comm_capacity = []
        # built the abstract comm for all comms that have not been assigned yet
        for (c, o) in itertools.product(sdf_multicore_sub.comms, reversed(orderings)):
            # check if any of the elements are already in the abstract communicator
            if not any(c in l or o in l for l in comms):
                comms.append([c, o])
        for l in comms:
            c = l[0]
            if isinstance(c, TimeDivisionMultiplexer):
                comm_capacity.append(c.get_slots())
            else:
                comm_capacity.append(1)
        orderings = sdf_multicore_sub.sdf_orders_sub.orderings
        pre_scheduling = {j: 0 for j in jobs}  #[-1 for (j, _) in enumerate(jobs)]
        pre_mapping = {j: 0 for j in jobs}  #[-1 for (j, _) in enumerate(jobs)]
        for a in sdf_actors:
            for (p, proc) in enumerate(procs):
                # try to find if there's a path between the job and the
                # processors. Assume every edge is a different mapping
                core = next(u for u in proc if isinstance(u, AbstractProcessingComponent))
                # use shortest path algorithms since we want a clear path without any
                # repetitions or cycles
                paths = nx.all_shortest_paths(model, core, a)
                # can only have one actor, the one in the loop
                paths = (path for path in paths if sum(1 for u in path if isinstance(u, Process)) == 1)
                # cn only have one core, the one in the loop
                paths = (path for path in paths
                         if sum(1 for u in path if isinstance(u, AbstractProcessingComponent)) == 1)
                # count the remaining paths now
                hits = sum(1 for _ in paths)
                for i in range(hits):
                    pre_mapping[(i, a)] = p
            # go through the trigger times to fetch when the actor might be
            # activated
            connected_orderings: Iterator[TimeTriggeredScheduler] = (
                o for o in orderings if model.has_edge(o, a) and a.identifier in o.get_trigger_time().values())
            triggers: Collection[int] = sorted((int(k) for (k, v) in o.get_trigger_time().items() if v == a.identifier
                                                for o in connected_orderings))
            for (i, t) in enumerate(triggers):
                # pass all the timings to the job
                pre_scheduling[(i, a)] = t
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
            throughput_importance = max((int(v.properties['apriori_importance']) for v in throughput_vertexes),
                                        default=0)
        comm_jobs = {((jsi, js), (jti, jt)): sdf_channels[(js, jt)]
                     for (jsi, js) in jobs for (jti, jt) in jobs if (js, jt) in sdf_channels}
        res = JobScheduling(abstracted=list(sdf_multicore_sub.covered_vertexes()),
                            comms=comms,
                            procs=procs,
                            jobs=jobs,
                            comm_jobs=comm_jobs,
                            weak_next=weak_next,
                            strong_next=strong_next,
                            paths=sdf_multicore_sub.connections,
                            pre_mapping=pre_mapping,
                            pre_scheduling=pre_scheduling,
                            goals_vertexes=goals_vertexes,
                            objective_weights=[throughput_importance, latency_importance],
                            wcet=np.zeros((len(jobs), len(procs)), dtype=np.uint64),
                            wcct=np.zeros((len(jobs), len(jobs), len(comms)), dtype=np.uint64))
    if res:
        return (True, res)
    elif sdf_multicore_sub and not res:
        return (True, None)
    else:
        return (False, None)


@register_identification_rule
def identify_instrumentation_in_jobs_from_sdf(model: ForSyDeModel, identified: List[DecisionModel]):
    res = None
    sub_jobs: Optional[JobScheduling] = next((p for p in identified if isinstance(p, JobScheduling)), None)
    sub_sdf: Optional[SDFExecution] = next((p for p in identified if isinstance(p, SDFExecution)), None)
    if sub_jobs and sub_sdf:
        time_scale = 1
        instrumented_procs = [p for proc in sub_jobs.procs for p in proc if isinstance(p, InstrumentedProcessorTile)]
        if all(a in sub_sdf.sdf_impl for (_, a) in sub_jobs.jobs) and len(instrumented_procs) == len(sub_jobs.procs):
            worst_cycles = np.zeros((len(sub_jobs.jobs), len(instrumented_procs)), dtype=np.uint64)
            for ((jidx, j), (pidx, p)) in itertools.product(sub_jobs.jobs, enumerate(instrumented_procs)):
                impl = sub_sdf.sdf_impl[j]
                worst_cycles[jidx, pidx] += p.get_clock_cycles_per_float_op() * impl.get_max_float_operations()
                worst_cycles[jidx, pidx] += p.get_clock_cycles_per_integer_op() * impl.get_max_int_operations()
                worst_cycles[jidx, pidx] += p.get_clock_cycles_per_boolean_op() * impl.get_max_boolean_operations()
                # wcet[jidx, pidx] = math.ceil(float(wcet[jidx, pidx]) / float(p.get_min_frequency_hz()))
            frequency_matrix = np.zeros((len(sub_jobs.jobs), len(instrumented_procs)), dtype=np.uint64)
            for ((jidx, j), (pidx, p)) in itertools.product(sub_jobs.jobs, enumerate(instrumented_procs)):
                frequency_matrix[jidx, pidx] = p.get_min_frequency_hz()
            # scale up until it
            min_clocks = np.amin(worst_cycles[worst_cycles > 0])
            max_freq = np.amax(frequency_matrix[frequency_matrix > 0])
            while (time_scale * min_clocks) // max_freq == 0:
                time_scale *= 1000
            wcet = ((time_scale * worst_cycles) / frequency_matrix).astype(np.uint64)
            wcct = np.zeros((len(sub_jobs.jobs), len(sub_jobs.jobs), len(sub_jobs.comms)), dtype=np.uint64)
            for ((jidx, j), (jjidx, jj), (pidx, p)) in itertools.product(sub_jobs.jobs, sub_jobs.jobs,
                                                                         enumerate(sub_jobs.comms)):
                if (j, jj) in sub_sdf.sdf_channels:
                    paths = sub_sdf.sdf_channels[(j, jj)]
                    # add up all the signals in a channel, for every commuication element in the
                    # connection path
                    wcct[jidx, jjidx, pidx] = sum(
                        math.ceil(
                            float(
                                sum(c.get_max_elem_size_bytes() * c.get_max_elem_count() for path in paths
                                    for c in path if isinstance(c, InstrumentedSignal))) /
                            float(u.get_max_bandwith_bytes_per_sec())) for u in p
                        if isinstance(u, InstrumentedCommunicationInterconnect))
                    wcct = (np.ceil(wcct / time_scale)).astype(np.uint64)
            res = JobScheduling(abstracted=set(sub_jobs.covered_vertexes()),
                                comms=sub_jobs.comms,
                                procs=sub_jobs.procs,
                                jobs=sub_jobs.jobs,
                                comm_jobs=sub_jobs.comm_jobs,
                                weak_next=sub_jobs.weak_next,
                                strong_next=sub_jobs.strong_next,
                                paths=sub_jobs.paths,
                                pre_mapping=sub_jobs.pre_mapping,
                                pre_scheduling=sub_jobs.pre_scheduling,
                                goals_vertexes=sub_jobs.goals_vertexes,
                                objective_weights=sub_jobs.objective_weights,
                                wcet=wcet,
                                wcct=wcct,
                                time_scale=time_scale,
                                job_allowed_location=sub_jobs.job_allowed_location)
    if res:
        return (True, res)
    elif sub_jobs and sub_sdf and not res:
        return (True, None)
    else:
        return (False, None)


@register_identification_rule
def identify_location_req_in_jobs_from_sdf(model: ForSyDeModel, identified: List[DecisionModel]):
    res = None
    sub_jobs: Optional[JobScheduling] = next((p for p in identified if isinstance(p, JobScheduling)), None)
    sub_sdf: Optional[SDFExecution] = next((p for p in identified if isinstance(p, SDFExecution)), None)
    if sub_jobs and sub_sdf:
        location_req: Dict[JobType, Collection[int]] = dict()
        location_vertexes = [l for l in model if isinstance(l, LocationRequirement)]
        located_jobs = {l: [(i, j) for (i, j) in enumerate(sub_jobs.jobs) if j in model[l]] for l in location_vertexes}
        located_procs = {
            l: [(i, p) for (i, p) in enumerate(sub_jobs.procs) if any(u in model[l] for u in p)]
            for l in location_vertexes
        }
        for l in location_vertexes:
            jobs = located_jobs[l]
            procs = located_procs[l]
            for (i, j) in jobs:
                location_req[j] = [pi for (pi, p) in procs]
        res = JobScheduling(abstracted=set(sub_jobs.covered_vertexes()) | set(location_vertexes),
                            comms=sub_jobs.comms,
                            procs=sub_jobs.procs,
                            jobs=sub_jobs.jobs,
                            comm_jobs=sub_jobs.comm_jobs,
                            weak_next=sub_jobs.weak_next,
                            strong_next=sub_jobs.strong_next,
                            paths=sub_jobs.paths,
                            pre_mapping=sub_jobs.pre_mapping,
                            pre_scheduling=sub_jobs.pre_scheduling,
                            goals_vertexes=sub_jobs.goals_vertexes,
                            objective_weights=sub_jobs.objective_weights,
                            wcet=sub_jobs.wcet,
                            wcct=sub_jobs.wcct,
                            time_scale=sub_jobs.time_scale,
                            job_allowed_location=location_req)
    if res:
        return (True, res)
    elif sub_jobs and sub_sdf and not res:
        return (True, None)
    else:
        return (False, None)


@register_identification_rule
def identify_merge_job_scheduling_simple(model: ForSyDeModel,
                                         identified: List[DecisionModel]) -> Tuple[bool, Optional[DecisionModel]]:
    res = None
    sub_jobs: Collection[JobScheduling] = [p for p in identified if isinstance(p, JobScheduling)]
    if len(sub_jobs) > 1:
        equal_jobs = all(set(sub2.jobs) == set(sub1.jobs) for sub1 in sub_jobs for sub2 in sub_jobs)
        equal_procs = all(
            set(u for p in sub1.procs for u in p) == set(u for p in sub2.procs for u in p) for sub1 in sub_jobs
            for sub2 in sub_jobs)
        equal_comms = all(
            set(u for p in sub1.comms for u in p) == set(u for p in sub2.comms for u in p) for sub1 in sub_jobs
            for sub2 in sub_jobs)
        if equal_jobs and equal_procs and equal_comms:
            wcet = np.zeros(sub_jobs[0].wcet.shape, dtype=np.uint64)
            wcct = np.zeros(sub_jobs[0].wcct.shape, dtype=np.uint64)
            location_req = {}
            for s in sub_jobs:
                wcet = np.maximum(wcet, s.wcet)
                wcct = np.maximum(wcct, s.wcct)
                for (i, _) in enumerate(s.jobs):
                    if i in location_req and i in s.job_allowed_location:
                        location_req[i] = set(location_req[i]).union(set(s.job_allowed_location[i]))
                    elif i in s.job_allowed_location:
                        location_req[i] = s.job_allowed_location[i]
            abstracted = functools.reduce(lambda s1, s2: s1.union(s2), map(lambda s: set(s.abstracted), sub_jobs))
            res = JobScheduling(abstracted=abstracted,
                                comms=sub_jobs[0].comms,
                                procs=sub_jobs[0].procs,
                                jobs=sub_jobs[0].jobs,
                                comm_jobs=sub_jobs[0].comm_jobs,
                                weak_next=sub_jobs[0].weak_next,
                                strong_next=sub_jobs[0].strong_next,
                                paths=sub_jobs[0].paths,
                                pre_mapping=sub_jobs[0].pre_mapping,
                                pre_scheduling=sub_jobs[0].pre_scheduling,
                                goals_vertexes=sub_jobs[0].goals_vertexes,
                                objective_weights=sub_jobs[0].objective_weights,
                                wcet=wcet,
                                wcct=wcct,
                                time_scale=max(s.time_scale for s in sub_jobs),
                                job_allowed_location=location_req)
    if res and all(v in res.abstracted for v in model.nodes):
        return (True, res)
    else:
        return (False, None)


# @register_identification_rule
# def identify_sdf_multi_core_instrumented(model: ForSyDeModel, identified: List[DecisionModel]):
#     '''This 'IdentificationRule' add WCET and WCCT atop 'JobScheduling'
#     '''
#     res = None
#     sub_jobs = next((p for p in identified if isinstance(p, JobScheduling)), None)
#     if sub_jobs:
#         # sdf_actors = sub_jobs.sdf_orders_sub.sdf_exec_sub.sdf_actors
#         # sdf_channels = sub_jobs.sdf_orders_sub.sdf_exec_sub.sdf_channels
#         # cores = sub_jobs.cores
#         # comms = sub_jobs.comms
#         # list(model.get_vertexes(WCET.get_instance()))
#         wcet_vertexes = [w for w in model if isinstance(w, WCET)]
#         token_wcct_vertexes = [w for w in model if isinstance(w, WCCT)]  # list(model.get_vertexes(WCCT.get_instance()))
#         wcet = np.zeros((len(sub_jobs.jobs), len(sub_jobs.procs)), dtype=int)
#         token_wcct = np.zeros((len(sub_jobs.comm_jobs), len(sub_jobs.comms)), dtype=int)
#         for (aidx, a) in enumerate(sdf_actors):
#             for (pidx, p) in enumerate(cores):
#                 wcet_vertex = next(, None)
#                 if wcet_vertex:
#                     wcet[aidx, pidx] = max(w.get_time() for w in wcet_vertexes if (a in model[w]) and (p in model[w]))
#                     # (int(w.properties['time'])
#                     #  for w in model.predecessors(a) if w in model.predecessors(p) and isinstance(w, WCET)),
#                     # default=0)
#         # iterate through all elements of a channel and take the
#         # maximum of the WCCTs for that path, since in a channels it is
#         # expected that the data type is the same along the entire path
#         for (cidx, (s, t)) in enumerate(sdf_channels):
#             paths = sdf_channels[(s, t)]
#             for (pidx, p) in enumerate(comms):
#                 token_wcct[cidx, pidx] = sum(
#                     int(w.get_time()) for path in paths for e in path for w in token_wcct_vertexes
#                     if e in model[w] and p in model[w])
#         # although there should be only one Th vertex
#         # per application, we apply maximun just in case
#         # someone forgot to make sure there is only one annotation
#         # per application
#         goals_vertexes = [v for v in model if isinstance(v, Goal)]
#         throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
#         throughput_importance = 0
#         # check that all actors are covered by a throughput goal
#         if all(sum(1 for p in nx.all_simple_paths(model, g, a)) > 0 for g in throughput_vertexes for a in sdf_actors):
#             throughput_importance = max((int(v.properties['apriori_importance']) for v in throughput_vertexes),
#                                         default=0)
#         # if all wcets are valid, the model is considered characterized
#         if 0 not in np.unique(wcet):
#             res = SDFToMultiCoreCharacterized(sdf_mpsoc_sub=sdf_mpsoc_sub,
#                                               wcet_vertexes=wcet_vertexes,
#                                               token_wcct_vertexes=token_wcct_vertexes,
#                                               wcet=wcet,
#                                               token_wcct=token_wcct,
#                                               throughput_importance=throughput_importance,
#                                               latency_importance=0,
#                                               goals_vertexes=goals_vertexes)
#     if res:
#         res.compute_deduced_properties()
#         return (True, res)
#     elif sdf_mpsoc_sub and not res:
#         return (True, None)
#     else:
#         return (False, None)

# _standard_rules_classes = [
#     SDFAppRule, SDFOrderRule, SDFToCoresRule, SDFToCoresCharacterizedRule, SDFMulticoreToJobsRule
# ]


@register_identification_rule
def identify_time_triggered_platform(model: ForSyDeModel,
                                     identified: Collection[DecisionModel]) -> IdentificationOutput:
    schedulers = [o for o in model if isinstance(o, TimeTriggeredScheduler)]
    cores = [p for p in model if isinstance(p, AbstractProcessingComponent)]
    comms = [p for p in model if isinstance(p, AbstractCommunicationComponent)]
    # find all cores that are connected between each other
    path: Dict[Tuple[Vertex, Vertex], Collection[Collection[Vertex]]] = {}
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
        p: p.get_max_memory_internal_bytes() if isinstance(p, InstrumentedProcessorTile) else 0
        for p in cores
    }
    # TODO: Identify also connected memory elements!

    core_scheduler = {p: next((o for o in schedulers if o in model[p]), None) for p in cores}
    comm_scheduler = {p: next((o for o in schedulers if o in model[p]), None) for p in comms}
    # second pass to now put all free schedulers together
    for (o, p) in itertools.product(schedulers, cores):
        if not core_scheduler[p] and o not in core_scheduler.values():
            core_scheduler[p] = o
    for (o, p) in itertools.product(schedulers, comms):
        if not comm_scheduler[p] and o not in comm_scheduler.values():
            comm_scheduler[p] = o
    res = TimeTriggeredPlatform(schedulers=schedulers,
                                comms_bandwidth=comms_bandwidth,
                                core_memory=core_memory,
                                core_scheduler=core_scheduler,
                                comm_scheduler=comm_scheduler,
                                paths=path,
                                abstracted=set(schedulers) | set(cores) | set(comms))
    return (True, res)


#class SDFToCoresRule(IdentificationRule):


@register_identification_rule
def identify_jobs_sdf_time_trigger_multicore(model: ForSyDeModel, identified: List[DecisionModel]):
    '''This 'IdentificationRule' identifies processing units

    The 'AbstractProcessingComponent' can communicate with each other
    if they are connected by one or more 'AbstractCommunicationComponent's.
    Otherwise, the processors are considered unreachable islands.

    It is assumed that the shortest path between two 'AbstractProcessingComponent'
    is _always_ the one chosen for data communication. Regardless on how
    the 'AbstractCommunicationComponent' communicates.
    '''
    sdf_app_sub = next((p for p in identified if isinstance(p, SDFExecution)), None)
    time_trig_platform_sub = next((p for p in identified if isinstance(p, TimeTriggeredPlatform)), None)
    all_covered = all(
        any(v in ident.covered_vertexes() for ident in identified) for v in model if isinstance(v, Process)
        or isinstance(v, AbstractProcessingComponent) or isinstance(v, TimeTriggeredScheduler))
    # all things to be identified are already there but not dependent model
    if all_covered and not sdf_app_sub and not time_trig_platform_sub:
        return (True, None)
    # still waiting for dependent models
    elif not sdf_app_sub or not time_trig_platform_sub:
        return (False, None)
    jobs, weak_next, strong_next = sdf_lib.sdf_to_jobs(sdf_app_sub.sdf_actors, sdf_app_sub.sdf_channels,
                                                       sdf_app_sub.sdf_topology, sdf_app_sub.sdf_repetition_vector,
                                                       sdf_app_sub.sdf_initial_tokens)
    goals_vertexes = [v for v in model if isinstance(v, Goal)]
    throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
    throughput_importance = 0
    latency_importance = 0
    # check that all actors are covered by a throughput goal
    if all(nx.has_path(model, g, a) for g in throughput_vertexes for a in sdf_app_sub.sdf_actors):
        throughput_importance = max((int(v.properties['apriori_importance']) for v in throughput_vertexes), default=0)
    comm_jobs = {((jsi, js), (jti, jt)): sdf_app_sub.sdf_channels[(js, jt)]
                 for (jsi, js) in jobs for (jti, jt) in jobs if (js, jt) in sdf_app_sub.sdf_channels}
    res = JobScheduling(
        abstracted=set(sdf_app_sub.covered_vertexes()) | set(time_trig_platform_sub.covered_vertexes())
        | set(goals_vertexes),
        jobs=jobs,
        comm_jobs=comm_jobs,
        weak_next=weak_next,
        strong_next=strong_next,
        procs_capacity=[time_trig_platform_sub.core_memory[p] for p in time_trig_platform_sub.core_scheduler],
        comm_capacity=[time_trig_platform_sub.comms_bandwidth[p] for p in time_trig_platform_sub.comm_scheduler],
        goals_vertexes=goals_vertexes,
        objective_weights=[throughput_importance, latency_importance],
        procs=[[p, o] for (p, o) in time_trig_platform_sub.core_scheduler.items()],
        comms=[[p, o] for (p, o) in time_trig_platform_sub.comm_scheduler.items()])
    return (True, res)

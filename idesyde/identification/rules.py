import networkx as nx
import numpy as np
import sympy
from typing import List
from typing import Dict
from typing import Tuple

from forsyde.io.python.core import Vertex
from forsyde.io.python.types import SDFComb
from forsyde.io.python.types import SDFPrefix
from forsyde.io.python.types import Process
from forsyde.io.python.types import Signal
from forsyde.io.python.types import AbstractOrdering
from forsyde.io.python.types import AbstractProcessingComponent
from forsyde.io.python.types import AbstractCommunicationComponent
from forsyde.io.python.types import WCET
from forsyde.io.python.types import WCCT
from forsyde.io.python.types import Goal
from forsyde.io.python.types import Output
from forsyde.io.python.types import MinimumThroughput
from forsyde.io.python.types import TimeDivisionMultiplexer

import idesyde.math as math_util
import idesyde.sdf as sdf_lib
from idesyde.identification.interfaces import IdentificationRule
from idesyde.identification.models import SDFExecution
from idesyde.identification.models import SDFToOrders
from idesyde.identification.models import SDFToMultiCore
from idesyde.identification.models import SDFToMultiCoreCharacterized
from idesyde.identification.models import CharacterizedJobShop


class SDFAppRule(IdentificationRule):

    def identify(self, model, identified):
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
        sdf_actors: List[Vertex] = [a for c in constructors for a in model.adj[c] if isinstance(a, Process)]
        # 1: find the delays
        sdf_delays: List[Vertex] = [a for c in delay_constructors for a in model.adj[c] if isinstance(a, Process)]
        # 1: get connected signals
        sdf_channels: List[Tuple[Vertex, Vertex, List[Vertex]]] = []
        # 1: check the model for the paths between actors
        for s in sdf_actors:
            for t in sdf_actors:
                if s != t:
                    try:
                        for path in nx.all_shortest_paths(model, s, t):
                            # take away the source and target nodes
                            path = path[1:-1]
                            # check if all elements in the path are signals or delays
                            if all(isinstance(v, Signal) or v in sdf_delays for v in path):
                                sdf_channels.append((s, t, path))
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
        initial_tokens = np.array([sum(1 for v in p if v in sdf_delays) for (_, _, p) in sdf_channels], dtype=int)
        # 1: build the topology matrix
        sdf_topology = np.zeros((len(sdf_channels), len(sdf_actors)), dtype=int)
        for (cidx, (s, t, path)) in enumerate(sdf_channels):
            sidx = sdf_actors.index(s)
            tidx = sdf_actors.index(t)
            # get the relevant port for the source and target actors
            # in this channel, assuming there is only one edge
            # connecting them
            # TODO: maybe find a way to generalize properly to multigraphs?
            out_port = next(v["object"].source_vertex_port for (k, v) in model[s][path[0]].items())
            in_port = next(v["object"].target_vertex_port for (k, v) in model[path[-1]][t].items())
            # get the constructor of the actors
            s_constructor = next(c for c in constructors if s in model[c])
            t_constructor = next(c for c in constructors if t in model[c])
            # look in their properties what is the production associated
            # with the channel, for the source...
            sdf_topology[cidx, sidx] = int(s_constructor.get_production()[out_port.identifier])
            sdf_topology[cidx, tidx] = - \
                int(t_constructor.get_consumption()[in_port.identifier])
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


class SDFOrderRule(IdentificationRule):
    '''This Rule Identifies possible parallel ordered schedules atop 'SDFExecution'.

    Since only the ordered schedules are considered, the "time" in the resulting
    model is still abstract.
    '''

    def identify(self, model, identified):
        res = None
        sdf_exec_sub = next((p for p in identified if isinstance(p, SDFExecution)), None)
        if sdf_exec_sub:
            orderings = [o for o in model if isinstance(o, AbstractOrdering)]
            if orderings:
                res = SDFToOrders(sdf_exec_sub=sdf_exec_sub, orderings=orderings)
        # conditions for fixpoints and partial identification
        if res:
            res.compute_deduced_properties()
            return (True, res)
        elif sdf_exec_sub and not res:
            return (True, None)
        else:
            return (False, None)


class SDFToCoresRule(IdentificationRule):
    '''This 'IdentificationRule' identifies processing units atop 'SDFToOrders'

    The 'AbstractProcessingComponent' can communicate with each other
    if they are connected by one or more 'AbstractCommunicationComponent's.
    Otherwise, the processors are considered unreachable islands.

    It is assumed that the shortest path between two 'AbstractProcessingComponent'
    is _always_ the one chosen for data communication. Regardless on how
    the 'AbstractCommunicationComponent' communicates.
    '''

    def identify(self, model, identified):
        res = None
        sdf_orders_sub = next((p for p in identified if isinstance(p, SDFToOrders)), None)
        if sdf_orders_sub:
            cores = [p for p in model if isinstance(p, AbstractProcessingComponent)]
            comms = [p for p in model if isinstance(p, AbstractCommunicationComponent)]
            # find all cores that are connected between each other
            connections: List[Tuple[Vertex, Vertex, List[Vertex]]] = []
            for s in cores:
                for t in cores:
                    if s != t:
                        try:
                            for path in nx.all_shortest_paths(model, s, t):
                                path = path[1:-1]
                                if all(isinstance(v, AbstractCommunicationComponent) for v in path):
                                    connections.append((s, t, path))
                        except nx.exception.NetworkXNoPath:
                            pass
            # for (cidx, (s, t, _)) in enumerate(connections):
            #     for path in nx.all_shortest_paths(model, s, t):
            #         path = path[1:-1]
            #         if all(isinstance(v, AbstractCommunicationComponent) for v in path):
            #             connections[cidx] = (s, t, path)
            # take away any non connected paths
            # connections = [(s, t, p) for (s, t, p) in connections if p]
            # there must be orderings for both execution and communication
            comms_capacity = [1 for c in comms]
            for (i, c) in enumerate(comms):
                if isinstance(c, TimeDivisionMultiplexer):
                    comms_capacity[i] = int(c.get_slots())
            if len(cores) + len(comms) >= len(sdf_orders_sub.orderings):
                res = SDFToMultiCore(sdf_orders_sub=sdf_orders_sub,
                                     cores=cores,
                                     comms=comms,
                                     connections=connections,
                                     comms_capacity=comms_capacity)
        # conditions for fixpoints and partial identification
        if res:
            res.compute_deduced_properties()
            return (True, res)
        elif not res and sdf_orders_sub:
            return (True, None)
        else:
            return (False, None)


class SDFToCoresCharacterizedRule(IdentificationRule):
    '''This 'IdentificationRule' add WCET and WCCT atop 'SDFToCoresRule'
    '''

    def identify(self, model, identified):
        res = None
        sdf_mpsoc_sub = next((p for p in identified if isinstance(p, SDFToMultiCore)), None)
        if sdf_mpsoc_sub:
            sdf_actors = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
            sdf_channels = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
            cores = sdf_mpsoc_sub.cores
            comms = sdf_mpsoc_sub.comms
            # list(model.get_vertexes(WCET.get_instance()))
            wcet_vertexes = [w for w in model if isinstance(w, WCET)]
            token_wcct_vertexes = [w for w in model
                                   if isinstance(w, WCCT)]  # list(model.get_vertexes(WCCT.get_instance()))
            wcet = np.zeros((len(cores), len(sdf_actors)), dtype=int)
            token_wcct = np.zeros((len(sdf_channels), len(comms)), dtype=int)
            # information is available for all actors
            # for all p,a; exists a wcet connected to them
            # actors_characterized = all(
            #     any(
            #         len(nx.all_simple_paths(model, w, p)) > 0 and
            #         len(nx.all_simple_paths(model, w, a)) > 0
            #         for w in wcet_vertexes
            #     )
            #     for a in sdf_actors for p in cores
            # )
            # info is available for all signals within a channel
            # forall p,c; exists a wcct connect to them (all signals in c)
            # channels_characterized = all(
            #     any(
            #         len(nx.all_simple_paths(model, w, p)) > 0 and
            #         all(len(nx.all_simple_paths(model, w, s)) > 0 for s in channel if isinstance(s, Signal))
            #         for w in token_wcct_vertexes
            #     )
            #     for (_, _, channel) in sdf_channels for p in comms
            # )
            # iterate through all actors and processes and note down
            # the WCET resulting from their interaction
            for (aidx, a) in enumerate(sdf_actors):
                for (pidx, p) in enumerate(cores):
                    wcet[aidx, pidx] = max(
                        (int(w.properties['time'])
                         for w in model.predecessors(a) if w in model.predecessors(p) and isinstance(w, WCET)),
                        default=0)
            # iterate through all elements of a channel and take the
            # maximum of the WCCTs for that path, since in a channels it is
            # expected that the data type is the same along the entire path
            for (cidx, (_, _, path)) in enumerate(sdf_channels):
                for (pidx, p) in enumerate(comms):
                    token_wcct[cidx, pidx] = max(
                        (int(w.properties['time']) for e in path
                         for w in model.predecessors(e) if w in model.predecessors(p) and isinstance(w, WCCT)),
                        default=0)
            # although there should be only one Th vertex
            # per application, we apply maximun just in case
            # someone forgot to make sure there is only one annotation
            # per application
            goals_vertexes = [v for v in model if isinstance(v, Goal)]
            throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
            throughput_importance = 0
            # check that all actors are covered by a throughput goal
            if all(
                    sum(1 for p in nx.all_simple_paths(model, g, a)) > 0 for g in throughput_vertexes
                    for a in sdf_actors):
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


class SDFMulticoreToJobsRule(IdentificationRule):

    def identify(self, model, identified):
        res = None
        sdf_mpsoc_char_sub: SDFToMultiCoreCharacterized = next(
            (p for p in identified if isinstance(p, SDFToMultiCoreCharacterized)), None)
        if sdf_mpsoc_char_sub:
            sdf_actors = sdf_mpsoc_char_sub.\
                sdf_mpsoc_sub.\
                sdf_orders_sub.\
                sdf_exec_sub.sdf_actors
            jobs, next_job = sdf_lib.sdf_to_hsdf(
                sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors,
                sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels,
                sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_topology,
                sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_repetition_vector,
                sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_initial_tokens)
            procs = []
            for (i, p) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.cores):
                orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings[i]
                procs.append([p, orderings])
            # one ordering per comm
            comms = []
            for (i, p) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms):
                orderings = sdf_mpsoc_char_sub.sdf_mpsoc_sub.sdf_orders_sub.orderings[i + len(procs)]
                comms.append([p, orderings])
            # fetch the wccts and wcets
            wcet = np.zeros((len(jobs), len(procs)), dtype=int)
            wcct = np.zeros((len(jobs), len(jobs), len(comms)), dtype=int)
            for (j, job) in enumerate(jobs):
                a = sdf_actors.index(job)
                for (i, _) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.cores):
                    wcet[j, i] = sdf_mpsoc_char_sub.wcet[a, i]
                for (jj, jjob) in enumerate(jobs):
                    aa = sdf_actors.index(jjob)
                    for (i, _) in enumerate(sdf_mpsoc_char_sub.sdf_mpsoc_sub.comms):
                        # TODO: Fix here with SDF channels!!!
                        wcct[j, jj, i] = 0  #sdf_mpsoc_char_sub.token_wcct[a, aa, i]
            res = CharacterizedJobShop(comms=comms, procs=procs, jobs=jobs, next_job=next_job)
        if res:
            return (True, res)
        else:
            return (False, None)


_standard_rules_classes = [
    SDFAppRule, SDFOrderRule, SDFToCoresRule, SDFToCoresCharacterizedRule, SDFMulticoreToJobsRule
]

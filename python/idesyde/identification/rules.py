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
from forsyde.io.python.types import MinimumThroughput

import idesyde.math as math_util
import idesyde.sdf as sdf_lib
from idesyde.identification.interfaces import IdentificationRule
from idesyde.identification.models import SDFExecution
from idesyde.identification.models import SDFToOrders
from idesyde.identification.models import SDFToMultiCore
from idesyde.identification.models import SDFToMultiCoreCharacterized
from idesyde.identification.models import SDFToMultiCoreCharacterizedJobs


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
        sdf_channels: List[Tuple[Vertex, Vertex,
                                 List[Vertex]]] = [(s, t, []) for s in sdf_actors for t in sdf_actors if s != t]
        # 1: check the model for the paths between actors
        for (cidx, (s, t, _)) in enumerate(sdf_channels):
            for path in nx.all_shortest_paths(model, s, t):
                # take away the source and target nodes
                path = path[1:-2]
                # check if all elements in the path are signals or delays
                if all(isinstance(v, Signal) or v in sdf_delays for v in path):
                    sdf_channels[cidx] = (s, t, path)
        # 1: remove all pre-built sdf channels that are empty
        sdf_channels = [(s, t, e) for (s, t, e) in sdf_channels if e]
        # 2: define the initial tokens by counting the delays on every path
        initial_tokens = np.array([len(v for v in p if v in sdf_delays) for (_, _, p) in sdf_channels], dtype=int)
        # 1: build the topology matrix
        sdf_topology = np.zeros((len(sdf_channels), len(sdf_actors)), dtype=int)
        for (cidx, (s, t, path)) in enumerate(sdf_channels):
            sidx = sdf_actors.index(s)
            tidx = sdf_actors.index(t)
            # get the constructor of the actors
            s_constructor = next(c for c in constructors if s in model.adj[c])
            t_constructor = next(c for c in constructors if t in model.adj[c])
            # look in their properties what is the production associated
            # with the channel, for the source...
            sdf_topology[cidx, sidx] = int(next(
                v for (k, v) in s_constructor.get_production().items()
                if k in (sig.identifier for sig in path)
            ))
            # .. and for the target
            sdf_topology[cidx, tidx] = -int(next(
                v for (k, v) in t_constructor.get_consumption().items()
                if k in (sig.identifier for sig in path)
            ))
        # for (a_index, actor) in enumerate(sdf_actors):
        #     constructor = next(c for c in constructors if actor in model.adj[c])
        #     for (c_index, channel) in enumerate(sdf_channels):
        #         # channel is in the forwards path, therefore written to
        #         # TODO: Fix here!!! Not ready for production!
        #         for (edix, edata, extra) in model.edges[actor, channel]:
        #             edge = edata['object']
        #         if channel in model[actor]:
        #             tokens = int(constructor.properties["production"][channel.identifier])
        #             sdf_topology[c_index, a_index] = tokens
        #         # channel is in the backwards path, therefore read from
        #         elif actor in model.adj[channel]:
        #             tokens = -int(constructor.properties["consumption"][channel.identifier])
        #             sdf_topology[c_index, a_index] = tokens
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
            connections = [(s, t, []) for s in cores for t in cores if s != t]
            for (cidx, (s, t, _)) in enumerate(connections):
                for path in nx.all_shortest_paths(model, s, t):
                    path = path[1:-2]
                    if all(isinstance(v, AbstractCommunicationComponent) for v in path):
                        connections[cidx] = (s, t, path)
            # take away any non connected paths
            connections = [(s, t, p) for (s, t, p) in connections if p]
            # there must be orderings for both execution and communication
            if len(cores) + len(comms) >= len(sdf_orders_sub.orderings):
                res = SDFToMultiCore(sdf_orders_sub=sdf_orders_sub, cores=cores, comms=comms, connections=connections)
        # conditions for fixpoints and partial identification
        if res:
            res.compute_deduced_properties()
            return (True, res)
        elif not res and sdf_orders_sub:
            return (True, None)
        else:
            return (False, None)


class SDFToCoresCharacterizedRule(IdentificationRule):

    def identify(self, model, identified):
        res = None
        sdf_mpsoc_sub = next((p for p in identified if isinstance(p, SDFToMultiCore)), None)
        if sdf_mpsoc_sub:
            sdf_actors = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_actors
            sdf_channels = sdf_mpsoc_sub.sdf_orders_sub.sdf_exec_sub.sdf_channels
            cores = sdf_mpsoc_sub.cores
            comms = sdf_mpsoc_sub.comms
            units = cores + comms
            wcet_vertexes = [w for w in model if isinstance(w, WCET)] # list(model.get_vertexes(WCET.get_instance()))
            token_wcct_vertexes = [w for w in model if isinstance(w, WCCT)] # list(model.get_vertexes(WCCT.get_instance()))
            wcet = np.zeros((len(cores), len(sdf_actors)), dtype=int)
            token_wcct = np.zeros((len(sdf_channels), len(units)), dtype=int)
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
            for (aidx, a) in enumerate(sdf_actors):
                for (pidx, p) in enumerate(cores):
                    w = next((v for v in nx.predecessors(a)
                              if v in nx.predecessors(p)
                              and isinstance(v, WCET)), None)
                    wcet[aidx, pidx] = int(w.properties['time']) if w else 0
            for (cidx, c) in enumerate(sdf_channels):
                for (pidx, p) in enumerate(comms):
                    w = next((v for v in nx.predecessors(c)
                              if v in nx.predecessors(p)
                              and isinstance(v, WCET)), None)
                    token_wcct[aidx, pidx] = int(w.properties['time']) if w else 0
            # although there should be only one Th vertex
            # per application, we apply maximun just in case
            # someone forgot to make sure there is only one annotation
            # per application
            goals_vertexes = [v for v in model if isinstance(v, Goal)]
            throughput_vertexes = [v for v in goals_vertexes if isinstance(v, MinimumThroughput)]
            throughput_importance = 0
            # check that all actors are covered by a throughput goal
            if all(
                    any(
                        len(nx.all_simple_paths(model, g, a)) > 0 
                        for a in sdf_actors for g in throughput_vertexes
                    )
            ):
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
        sdf_mpsoc_char_sub = next((p for p in identified if isinstance(p, SDFToMultiCoreCharacterized)), None)
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
                for (aidx, a) in enumerate(sdf_actors) for i in sdf_repetition_vector[aidx]
            }
            jobs = set(jobs_actors.keys())
            res = SDFToMultiCoreCharacterizedJobs(sdf_mpsoc_char_sub=sdf_mpsoc_char_sub,
                                                  jobs_actors=jobs_actors,
                                                  jobs=jobs)
        if res:
            return (True, res)
        else:
            return (False, None)


_standard_rules_classes = [
    SDFAppRule, SDFOrderRule, SDFToCoresRule, SDFToCoresCharacterizedRule, SDFMulticoreToJobsRule
]

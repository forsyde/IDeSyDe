import networkx as nx
import numpy as np
import sympy
from typing import List
from typing import Dict
from typing import Tuple

from forsyde.io.python.core import Vertex
from forsyde.io.python.types import SDFCombType
from forsyde.io.python.types import SDFDelayType
from forsyde.io.python.types import ProcessType
from forsyde.io.python.types import SignalType
from forsyde.io.python.types import AbstractOrderingType
from forsyde.io.python.types import AbstractProcessingComponentType
from forsyde.io.python.types import AbstractCommunicationComponentType
from forsyde.io.python.types import WCETType

import idesyde.math as mathutil
import idesyde.sdf as sdfapi
from idesyde.identification.interfaces import IdentificationRule
from idesyde.identification.models import SDFExecution
from idesyde.identification.models import SDFToOrders
from idesyde.identification.models import SDFToMultiCore
from idesyde.identification.models import SDFToMultiCoreCharacterized
from idesyde.identification.models import SDFToMultiCoreCharacterizedJobs


class SDFAppRule(IdentificationRule):

    def identify(self, model, identified):
        '''This Rule identifies (H)SDF applications that are valid

        To be valid, the (H)SDF applications must:
            1. The topology matrix must have a null space of dimension 1,
                if there exists at least one channel in the application.
            2. There must be a PASS for the application.
        '''
        result = None
        constructors = [c for c in model.get_vertexes(SDFCombType.get_instance())]
        delay_constructors = [c for c in model.get_vertexes(SDFDelayType.get_instance())]
        # 1: find the actors
        sdf_actors: List[Vertex] = [
            a for c in constructors for a in model.adj[c]
            if a.is_type(ProcessType.get_instance())
        ]
        # 1: find the delays
        sdf_delays: List[Vertex] = [
            a for c in delay_constructors for a in model.adj[c]
            if a.is_type(ProcessType.get_instance())
        ]
        # 1: get connected signals
        sdf_channels: List[Tuple[Vertex, Vertex, List[Vertex]]] = [
            (s, t, [])
            for s in sdf_actors
            for t in sdf_actors
            if s != t
        ]
        # 1: check the model for the paths between actors
        for (cidx, (s, t, _)) in enumerate(sdf_channels):
            for path in nx.all_simple_paths(model, s, t):
                # check if all elements in the path are signals or delays
                if all(
                        v.is_type(SignalType.get_instance()) or
                        v in sdf_delays
                        for v in path
                ):
                    sdf_channels[cidx] = (s, t, path)
        # 1: remove all pre-built sdf channels that are empty
        sdf_channels = [(s, t, e) for (s, t, e) in sdf_channels if e]
        # 2: define the initial tokens by counting the delays on every path
        initial_tokens = np.array(
            [len(v for v in p if v in sdf_delays) for (_, _, p) in sdf_channels],
            dtype=int
        )
        # 1: build the topology matrix
        sdf_topology = np.zeros((len(sdf_channels), len(sdf_actors)), dtype=int)
        for (a_index, actor) in enumerate(sdf_actors):
            constructor = next(c for c in constructors if actor in model.adj[c])
            for (c_index, channel) in enumerate(sdf_channels):
                # channel is in the forwards path, therefore written to
                # TODO: Fix here!!! Not ready for production!
                for (edix, edata, extra) in model.edges[actor, channel]:
                    edge = edata['object']
                if channel in model[actor]:
                    tokens = int(constructor.properties["production"][channel.identifier])
                    sdf_topology[c_index, a_index] = tokens
                # channel is in the backwards path, therefore read from
                elif actor in model.adj[channel]:
                    tokens = -int(constructor.properties["consumption"][channel.identifier])
                    sdf_topology[c_index, a_index] = tokens
        # 1.4 calculate the null space
        null_space = sympy.Matrix(sdf_topology).nullspace()
        if len(null_space) == 1:
            repetition_vector = mathutil.integralize_vector(null_space[0])
            repetition_vector = np.array(repetition_vector, dtype=int)
            # TODO: this must be fixed since it always assumes zero tokens!
            initial_tokens = np.zeros((sdf_topology.shape[0], 1))
            schedule = sdfapi.get_PASS(sdf_topology, repetition_vector, initial_tokens)
            if schedule != []:
                sdf_pass = [sdf_actors[idx] for idx in schedule]
                result = SDFExecution(sdf_actors=sdf_actors,
                                   sdf_channels=sdf_channels,
                                   sdf_topology=sdf_topology,
                                   sdf_repetition_vector=repetition_vector,
                                   sdf_pass=sdf_pass)
        # conditions for fixpoints and partial identification
        if result:
            result.compute_deduced_properties()
            return (True, result)
        else:
            return (False, None)


class SDFOrderRule(IdentificationRule):

    def identify(self, model, identified):
        res = None
        sdf_exec_sub = next((p for p in identified if isinstance(p, SDFExecution)), None)
        if sdf_exec_sub:
            orderings = list(model.get_vertexes(AbstractOrderingType.get_instance()))
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

    def identify(self, model, identified):
        res = None
        sdf_orders_sub = next((p for p in identified if isinstance(p, SDFToOrders)), None)
        if sdf_orders_sub:
            undirected = nx.to_undirected(model)
            cores = list(model.get_vertexes(AbstractProcessingComponentType.get_instance()))
            # connects to any processor
            busses = [
                c for c in model.get_vertexes(AbstractCommunicationComponentType.get_instance()) if any(
                    nx.has_path(undirected, p, c) for p in cores)
            ]
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
                res = SDFToMultiCore(sdf_orders_sub=sdf_orders_sub, cores=cores, busses=busses, connections=connections)
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
            busses = sdf_mpsoc_sub.busses
            units = cores + busses
            wcet_vertexes = list(model.get_vertexes(WCETType.get_instance()))
            token_wcct_vertexes = list(model.get_vertexes(WCCTType.get_instance()))
            wcet = None
            token_wcct = None
            # information is available for all actors and channels
            info_actors_is_ok = all(
                any(a in model.adj[w] and p in model.adj[w] for w in wcet_vertexes) for a in sdf_actors for p in cores)
            info_channels_is_ok = all(
                any(c in model.adj[w] and u in model.adj[w] for w in token_wcct_vertexes) for c in sdf_channels
                for u in units)
            if info_actors_is_ok and info_channels_is_ok:
                wcet = np.zeros((
                    len(cores),
                    len(sdf_actors),
                ), dtype=int, fill_value=np.inf)
                for w in wcet_vertexes:
                    for (adix, a) in enumerate(v in model.adj[w] for v in sdf_actors):
                        for (pidx, p) in enumerate(v in model.adj[w] for v in cores):
                            wcet[aidx, pidx] = int(w.properties['time'])
                token_wcct = np.zeros((len(sdf_channels), len(units)), dtype=int)
                for w in token_wcct_vertexes:
                    for (cidx, c) in enumerate(v in model.adj[w] for v in sdf_channels):
                        for (uidx, u) in enumerate(v in model.adj[w] for v in units):
                            token_wcct[cidx, udix] = int(w.properties['time'])
            # although there should be only one Th vertex
            # per application, we apply maximun just in case
            # someone forgot to make sure there is only one annotation
            # per application
            goals_vertexes = list(model.get_vertexes(GoalType.get_instance()))
            throughput_vertexes = [v for v in goals_vertexes if v.is_type(MinimunThroughputType.get_instance())]
            throughput_importance = 0
            # check that all actors are covered by a throughput goal
            if all(any(a in x.node_connected_component(model, g) for g in throughput_vertexes) for a in sdf_actors):
                throughput_importance = max((int(v.properties['apriori_importance']) for v in throughput_vertexes),
                                            default=0)
            if wcet is not None and token_wcct is not None:
                res = SDFToMultiCoreCharacterized(sdf_mpsoc_sub=sdf_mpsoc_sub,
                                                  wcet_vertexes=set(model.query_vertexes('wcet')),
                                                  token_wcct_vertexes=set(model.query_vertexes('signal_token_wcct')),
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

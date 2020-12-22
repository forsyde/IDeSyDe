import numpy as np
import sympy
from forsyde.io.python import Vertex

import idesyde.math as mathutil
import idesyde.sdf as sdfapi
from idesyde.identification.interfaces import IdentificationRule
from idesyde.identification.models import SDFExecution
from idesyde.identification.models import SDFToOrders
from idesyde.identification.models import SDFToMultiCore
from idesyde.identification.models import SDFToMultiCoreCharacterized
from idesyde.identification.models import SDFToMultiCoreCharacterizedJobs


class SDFExecRule(IdentificationRule):

    def identify(self, model, identified):
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


class SDFOrderRule(IdentificationRule):

    def identify(self, model, identified):
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


class SDFToCoresRule(IdentificationRule):

    def identify(self, model, identified):
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


class SDFToCoresCharacterizedRule(IdentificationRule):

    def identify(self, model, identified):
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
                res = SDFToMultiCoreCharacterized(
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


class SDFMulticoreToJobsRule(IdentificationRule):

    def identify(self, model, identified):
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
            res = SDFToMultiCoreCharacterizedJobs(
                sdf_mpsoc_char_sub=sdf_mpsoc_char_sub,
                jobs_actors=jobs_actors,
                jobs=jobs
            )
        if res:
            return (True, res)
        else:
            return (False, None)


_standard_rules_classes = [
    SDFExecRule,
    SDFOrderRule,
    SDFToCoresRule,
    SDFToCoresCharacterizedRule,
    SDFMulticoreToJobsRule
]

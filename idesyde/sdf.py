from typing import List, Optional, Dict, Tuple

import numpy as np
from forsyde.io.python.core import Vertex


def get_PASS(sdf_topology: np.ndarray,
             repetition_vector: np.ndarray,
             initial_tokens: Optional[np.ndarray] = None) -> List[int]:
    '''Returns the PASS of a SDF graph

    The calculation follows almost exactly what is dictated in the
    87 paper by LSV (Reference to be added later), except with some
    minor adaptations for numpy usage.

    Arguments:
        sdf_topology: The topology matrix of the SDF graph.
        repetition_vector: Number of firings for each Actor.
        initial_tokens: Initial tokens in each channels.

    Returns:
        A list of integers, each representing the index of the
        actor fired, in the order returned. E.g.

            [1, 9, 4]

        means:

            Actor 1 fires, then 9 then 4.
    '''
    if initial_tokens is None:
        initial_tokens = np.zeros((sdf_topology.shape[2], 1))
    tokens = initial_tokens
    repetition = np.array(repetition_vector, copy=True)
    firings = []
    num_firings = repetition.sum()
    for i in range(num_firings):
        for (idx, qi) in enumerate(repetition):
            if qi > 0:
                fire_vector = np.zeros((sdf_topology.shape[1], 1))
                fire_vector[idx] = 1
                candidate = np.dot(sdf_topology, fire_vector) + tokens
                if (candidate >= 0).all():
                    repetition[idx] -= 1
                    tokens = candidate
                    firings.append(idx)
                    break
    # if the schedule could not be built, return an empty list
    if len(firings) < num_firings:
        return []
    else:
        return firings


def check_sdf_consistency(sdf_topology) -> bool:
    return False


def sdf_to_hsdf(actors: List[Vertex], channels: List[Tuple[Vertex, Vertex, List[Vertex]]], topology: np.ndarray,
                repetition_vector: np.ndarray,
                initial_tokens: np.ndarray) -> Tuple[List[Vertex], List[Tuple[Vertex, Vertex]]]:
    jobs = [a for (i, a) in enumerate(actors) for _ in range(int(repetition_vector[i]))]
    next_job: List[List[Vertex]] = []
    for (i, (s, t, p)) in enumerate(channels):
        pass
    for j in jobs:
        for jj in jobs:
            if j != jj:
                pass
    return (jobs, next_job)

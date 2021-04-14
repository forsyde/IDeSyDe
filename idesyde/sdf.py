import math
from typing import List, Sequence, Optional, Dict, Tuple

import numpy as np
from forsyde.io.python.core import Vertex


def get_PASS(sdf_topology: np.ndarray,
             repetition_vector: np.ndarray,
             initial_tokens: Optional[np.ndarray] = None) -> Sequence[int]:
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
    firings: List[int] = []
    num_firings = int(repetition.sum())
    fire_vector = np.zeros((sdf_topology.shape[1], 1))
    for _ in range(num_firings):
        for (idx, q) in enumerate(repetition):
            if q > 0:
                fire_vector.fill(0)
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


def sdf_to_jobs(
        actors: Sequence[Vertex], channels: Sequence[Tuple[Vertex, Vertex, Sequence[Vertex]]], topology: np.ndarray,
        repetition_vector: np.ndarray,
        initial_tokens: np.ndarray) -> Tuple[Sequence[Vertex], Sequence[Tuple[int, int]], Sequence[Tuple[int, int]]]:
    '''Create job graph out of a SDF graph.

    This function returns a precedence graph of sdf 'jobs' so that any
    scheduling algorithm can work upon then directly if it is a variant
    of job shop scheduling. The returned graph has the notions of _weak_
    and _strong_ precedences:
    
        - if j2 weak proceeds j1, j1 must start before j2 starts.
        - if j2 strong proceeds j2, j1 must finish before j2 starts.

    Arguments:
        actors: The SDF actors.
        channels: The Channel representations between every actor.
        topology: The SDF topology matrix: consumptions and productions.
        repetition_vector: The amount of firings for each actor in actors.
            It is expected that the repetition vector is a column vector.
        initial_tokens: the delays for each channel of the SDF graph.

    Returns:
        A tuple containing 1) the actors as jobs, 2) the weak procededences
        and the 3) strong procedences.
    '''
    if repetition_vector.shape[1] != 1:
        raise TypeError("The repetition vector should be a column vector.")
    q_vector = repetition_vector.reshape(repetition_vector.size)
    actor_fire = [(a, q) for (i, a) in enumerate(actors) for q in range(1, int(q_vector[i]) + 1)]
    strong_next: List[Tuple[int, int]] = []
    for (cidx, (s, t, _)) in enumerate(channels):
        idxs = actors.index(s)
        idxt = actors.index(t)
        production = topology[cidx, idxs]
        consumption = topology[cidx, idxt]
        fires = 1
        firet = 1
        while firet <= q_vector[idxt]:
            if production * (fires - 1) + int(initial_tokens[cidx]) + consumption * firet >= 0:
                firet += 1
            else:
                poss = actor_fire.index((s, fires))
                post = actor_fire.index((t, firet))
                strong_next.append((poss, post))
                fires += 1
        # for fires in range(q_vector[idxs]):
        #     for firet in range(q_vector[idxt]):
        #         if production * fires + int(initial_tokens[cidx]) < consumption * (firet + 1):
        #             poss = actor_fire.index((s, fires))
        #             post = actor_fire.index((t, firet))
        #             strong_next.append((poss, post))
    jobs = [a for (a, q) in actor_fire]
    weak_next: List[Tuple[int, int]] = []
    for ((i, j), (inext, jnext)) in zip(enumerate(jobs[:-1]), enumerate(jobs[1:])):
        if j == jnext:
            # the +1 comes from the fact that we dont start at 0
            weak_next.append((i, inext + 1))
    return (jobs, weak_next, strong_next)

from idesyde.identification.models import JobType
from typing import List
from typing import Sequence
from typing import Optional
from typing import Mapping
from typing import Tuple
from typing import Collection

# import numpy as np
from forsyde.io.python.core import Vertex


def get_PASS(
    sdf_topology: List[List[int]], repetition_vector: List[int], initial_tokens: Optional[List[int]] = None
) -> Collection[int]:
    """Returns the PASS of a SDF graph

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
    """
    tokens = [b for b in initial_tokens] if initial_tokens is not None else [0 for _ in sdf_topology]
    repetition = [q for q in repetition_vector]
    firings: List[int] = []
    num_firings = sum(repetition)
    # we know the maximum number of steps
    for _ in range(num_firings):
        # we try firing every actor
        for (idx, q) in enumerate(repetition):
            # if they can be fired
            if q > 0:
                # we do the do product
                for (channeln, col) in enumerate(sdf_topology):
                    tokens[channeln] += col[idx]
                # and check if anything didnt go negative
                if all(v >= 0 for v in tokens):
                    repetition[idx] -= 1
                    firings.append(idx)
                    break
                # if so, we roll back and try gain
                else:
                    for (channeln, col) in enumerate(sdf_topology):
                        tokens[channeln] -= col[idx]
    # if the schedule could not be built, return an empty list
    if len(firings) < num_firings:
        return []
    else:
        return firings


def check_sdf_consistency(sdf_topology) -> bool:
    return False


def sdf_to_jobs(
    actors: Collection[Vertex],
    channels: Mapping[Tuple[Vertex, Vertex], Sequence[Sequence[Vertex]]],
    topology: List[List[int]],
    repetition_vector: List[int],
    initial_tokens: Optional[List[int]],
) -> Tuple[List[JobType], Mapping[JobType, List[JobType]], Mapping[JobType, List[JobType]]]:
    """Create job graph out of a SDF graph.

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
    """
    q_vector = [q for q in repetition_vector]
    jobs = [(q, a) for (i, a) in enumerate(actors) for q in range(1, int(q_vector[i]) + 1)]
    strong_next: Mapping[JobType, List[JobType]] = {j: [] for (i, j) in enumerate(jobs)}
    initial_tokens_internal = initial_tokens if initial_tokens else [0 for _ in actors]
    for (cidx, (s, t)) in enumerate(channels):
        idxs = next((i for (i, a) in enumerate(actors) if a == s), -1)
        idxt = next((i for (i, a) in enumerate(actors) if a == t), -1)
        production = topology[cidx][idxs]
        consumption = topology[cidx][idxt]
        fires = 1
        firet = 1
        while firet <= q_vector[idxt]:
            if production * (fires - 1) + initial_tokens_internal[cidx] + consumption * firet >= 0:
                firet += 1
            else:
                strong_next[(fires, s)].append((firet, t))
                fires += 1
        # for fires in range(q_vector[idxs]):
        #     for firet in range(q_vector[idxt]):
        #         if production * fires + int(initial_tokens[cidx]) < consumption * (firet + 1):
        #             poss = actor_fire.index((s, fires))
        #             post = actor_fire.index((t, firet))
        #             strong_next.append((poss, post))
    weak_next: Mapping[JobType, List[JobType]] = {j: [] for (_, j) in enumerate(jobs)}
    for ((i, j), (inext, jnext)) in zip(jobs[:-1], jobs[1:]):
        if j == jnext and inext == i + 1:
            # the +1 comes from the fact that we dont start at 0
            weak_next[(i, j)].append((inext, jnext))
    return (jobs, weak_next, strong_next)

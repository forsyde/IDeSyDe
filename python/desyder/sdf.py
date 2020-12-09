from typing import List, Optional, Dict, Tuple

import numpy as np

def get_PASS(sdf_topology: np.ndarray,
             repetition_vector: np.ndarray,
             initial_tokens: Optional[np.ndarray] = None
             ) -> List[str]:
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


def sdf_to_hsdf(
        sdf_topology: np.ndarray,
        repetition_vector: np.ndarray,
        initial_tokens: np.ndarray
        ) -> Tuple[Dict[int, List[int]], np.ndarray]:
    pass

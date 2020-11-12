import abc
from typing import Optional, List, Dict

from forsyde.io.python import ForSyDeModel
import minizinc


class MinizincDecideable(abc.ABC):

    @abs.abstractmethod
    def get_minizinc_model(self) -> minizinc.Model:
        return minizinc.Model()

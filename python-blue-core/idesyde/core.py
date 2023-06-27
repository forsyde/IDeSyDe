from dataclasses import dataclass, asdict, is_dataclass
from json import dumps
from typing import Dict, Optional, List


@dataclass
class ExplorationCombinationDescription:
    can_explore: bool = False
    criteria: Dict[str, float] = dict()

@dataclass
class DecisionModelHeader:
    category: str
    covered_elements: List[str] = list()
    body_path: Optional[str] = None

@dataclass
class DesignModelHeader:
    category: str
    elements: List[str] = list()
    model_paths: List[str] = list()

class DecisionModel:

    def unique_identifier(self) -> str:
        return self.__class__.__name__
    
    def header(self) -> DecisionModelHeader:
        return DecisionModelHeader(category=self.unique_identifier())
    
    def dominates(self, other: "DecisionModel") -> bool:
        return False
    
    def body_as_json(self) -> Optional[str]:
        if is_dataclass(self):
            return dumps(asdict(self))
        else:
            return None

    
class DesignModel:

    def unique_identifier(self) -> str:
        return self.__class__.__name__
    
    def header(self) -> DesignModelHeader:
        return DesignModelHeader(category=self.unique_identifier())
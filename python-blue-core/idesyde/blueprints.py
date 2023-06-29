from typing import Optional, Set

from .core import DecisionModel, DesignModel
from .core import DecisionModel, DesignModel

from idesyde.core import ExplorationModule, IdentificationModule, DesignModel, DecisionModel, DecisionModelHeader, IdentificationRuleT, ReverseIdentificationRuleT

class StandaloneIdentificationModule(IdentificationModule):

    def read_design_model(self, path: str) -> Optional[DesignModel]:
        return None
    
    def write_design_model(self, design_model: DesignModel, dest: str) -> bool;
        return False
    
    def decision_header_to_model(
        self,
        header: DecisionModelHeader,
    ) -> Optional[DecisionModel]:
        return None
    
    def identification_rules(self) -> Set[IdentificationRuleT]:
        return set()
    
    def reverse_identification_rules(self) -> Set[ReverseIdentificationRuleT]:
        return set()
    
    def decision_models_schemas(self) -> Set[str]:
        return set()
    
    def identification_step(self, iteration: int, design_models: Set[DesignModel], decision_models: Set[DecisionModel]) -> Set[DecisionModel]:
        identified = set();
        for irule in self.identification_rules():
            for m in irule(design_models, decision_models):
                if m not in identified:
                    identified.add(m)
        return identified
    
    def reverse_identification(self, decision_models: Set[DecisionModel], design_models: Set[DesignModel]) -> Set[DesignModel]:
        identified = set();
        for rirule in self.reverse_identification_rules():
            for m in rirule(decision_models, design_models):
                if m not in identified:
                    identified.add(m)
        return identified
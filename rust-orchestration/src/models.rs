use std::{collections::HashSet, path::Path};

use idesyde_blueprints::{DecisionModelMessage, DesignModelMessage, ExplorationSolutionMessage};
use idesyde_core::{
    headers::{DecisionModelHeader, DesignModelHeader},
    DecisionModel, DesignModel,
};
pub struct OpaqueDesignModel {
    header: DesignModelHeader,
    body: Option<String>,
}

impl OpaqueDesignModel {
    pub fn from_path(p: &Path) -> OpaqueDesignModel {
        let paths = p
            .to_str()
            .map(|x| vec![x.to_string()])
            .unwrap_or(Vec::new());
        OpaqueDesignModel {
            header: DesignModelHeader {
                category: "Opaque".to_string(),
                model_paths: paths,
                elements: HashSet::new(),
            },
            body: p
                .read_link()
                .ok()
                .and_then(|f| std::fs::read_to_string(f).ok()),
        }
    }

    pub fn from_path_str(s: &str) -> OpaqueDesignModel {
        OpaqueDesignModel {
            header: DesignModelHeader {
                category: "Opaque".to_string(),
                model_paths: vec![s.to_owned()],
                elements: HashSet::new(),
            },
            body: std::fs::read_to_string(Path::new(s)).ok(),
        }
    }
}

impl DesignModel for OpaqueDesignModel {
    fn category(&self) -> String {
        self.header.category.to_owned()
    }

    fn header(&self) -> DesignModelHeader {
        self.header.to_owned()
    }

    fn body_as_string(&self) -> Option<String> {
        self.body.to_owned()
    }
}

impl From<&DesignModelMessage> for OpaqueDesignModel {
    fn from(value: &DesignModelMessage) -> Self {
        OpaqueDesignModel {
            header: value.header.to_owned(),
            body: value.body.to_owned(),
        }
    }
}

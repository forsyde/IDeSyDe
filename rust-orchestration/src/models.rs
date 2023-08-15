use std::{collections::HashSet, path::Path};

use idesyde_blueprints::{DecisionModelMessage, DesignModelMessage};
use idesyde_core::{
    headers::{DecisionModelHeader, DesignModelHeader},
    DecisionModel, DesignModel,
};

#[derive(Eq, Clone)]
pub struct OpaqueDecisionModel {
    header: DecisionModelHeader,
    body_json: Option<String>,
    body_msgpack: Option<Vec<u8>>,
    body_cbor: Option<Vec<u8>>,
}

impl OpaqueDecisionModel {
    pub fn from_json_str(header: &DecisionModelHeader, s: &str) -> OpaqueDecisionModel {
        OpaqueDecisionModel {
            header: header.to_owned(),
            body_json: Some(s.to_string()),
            body_msgpack: None,
            body_cbor: None,
        }
    }
}

impl DecisionModel for OpaqueDecisionModel {
    fn category(&self) -> String {
        self.header.category.to_owned()
    }

    fn header(&self) -> DecisionModelHeader {
        self.header.to_owned()
    }

    fn body_as_json(&self) -> Option<String> {
        self.body_json.to_owned()
    }

    fn body_as_msgpack(&self) -> Option<Vec<u8>> {
        self.body_msgpack.to_owned()
    }

    fn body_as_cbor(&self) -> Option<Vec<u8>> {
        self.body_cbor.to_owned()
    }
}

impl PartialEq for OpaqueDecisionModel {
    fn eq(&self, other: &Self) -> bool {
        self.header == other.header && self.body_as_json() == other.body_as_json()
    }
}

impl From<&DecisionModelMessage> for OpaqueDecisionModel {
    fn from(value: &DecisionModelMessage) -> Self {
        OpaqueDecisionModel {
            header: value.header().to_owned(),
            body_json: value.body_with_newlines_unescaped().to_owned(),
            body_msgpack: None,
            body_cbor: None,
        }
    }
}

impl From<&DecisionModelHeader> for OpaqueDecisionModel {
    fn from(value: &DecisionModelHeader) -> Self {
        OpaqueDecisionModel {
            header: value.to_owned(),
            body_json: value
                .body_path
                .to_owned()
                .and_then(|x| std::fs::read_to_string(x).ok())
                .and_then(|x| serde_json::from_str(&x).ok()),
            body_msgpack: None,
            body_cbor: None,
        }
    }
}

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

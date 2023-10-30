use std::{collections::HashSet, path::Path};

use idesyde_blueprints::DesignModelMessage;
use idesyde_core::{headers::DesignModelHeader, DesignModel};
use log::debug;
pub struct OpaqueDesignModel {
    header: DesignModelHeader,
    body: Option<String>,
    extensions: Vec<String>,
}

impl OpaqueDesignModel {
    // pub fn from_path(p: &Path) -> OpaqueDesignModel {
    //     let paths = p
    //         .to_str()
    //         .map(|x| vec![x.to_string()])
    //         .unwrap_or(Vec::new());
    //     OpaqueDesignModel {
    //         header: DesignModelHeader {
    //             category: "Opaque".to_string(),
    //             model_paths: paths,
    //             elements: HashSet::new(),
    //         },
    //         body: p
    //             .read_link()
    //             .ok()
    //             .and_then(|f| std::fs::read_to_string(f).ok()),
    //         extensions: p
    //             .extension()
    //             .and_then(|x| x.to_str())
    //             .map(|x| x.to_string()),
    //     }
    // }

    pub fn from_path_str(s: &str) -> OpaqueDesignModel {
        OpaqueDesignModel {
            header: DesignModelHeader {
                category: "Opaque".to_string(),
                model_paths: vec![s.to_owned()],
                elements: HashSet::new(),
            },
            body: std::fs::read_to_string(Path::new(s)).ok(),
            extensions: vec![],
        }
    }
}

impl<'a> From<&'a Path> for OpaqueDesignModel {
    fn from(value: &'a Path) -> Self {
        let path = if value.is_symlink() {
            value
                .read_link()
                .expect("Should not fail due to a symlink check condition.")
        } else {
            value.to_path_buf()
        };
        // let paths = path.map(|x| vec![x.to_string()]).unwrap_or(Vec::new());
        OpaqueDesignModel {
            header: DesignModelHeader {
                category: "Opaque".to_string(),
                model_paths: path.to_str().map(|x| vec![x.to_string()]).unwrap_or(vec![]),
                elements: HashSet::new(),
            },
            body: match std::fs::read_to_string(&path) {
                Ok(ff) => Some(ff),
                Err(e) => {
                    debug!("Error while reading file: {}", e.to_string());
                    None
                }
            },
            // .and_then(|f|
            // }),
            extensions: path
                .extension()
                .and_then(|x| x.to_str())
                .map(|x| vec![x.to_string()])
                .unwrap_or(vec![]),
        }
    }
}

impl<'a> From<&'a str> for OpaqueDesignModel {
    fn from(value: &'a str) -> Self {
        OpaqueDesignModel {
            header: DesignModelHeader {
                category: "Opaque".to_string(),
                model_paths: vec![value.to_owned()],
                elements: HashSet::new(),
            },
            body: std::fs::read_to_string(Path::new(value)).ok(),
            extensions: vec![],
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

    fn extensions(&self) -> Vec<String> {
        return self.extensions.to_owned();
    }
}

impl From<&DesignModelMessage> for OpaqueDesignModel {
    fn from(value: &DesignModelMessage) -> Self {
        OpaqueDesignModel {
            header: value.header.to_owned(),
            body: value.body.to_owned(),
            extensions: value.extensions.to_owned(),
        }
    }
}

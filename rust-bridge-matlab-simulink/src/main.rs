use std::{
    collections::{HashMap, HashSet},
    process::Command,
};

use idesyde_blueprints::execute_standalone_identification_module;
use idesyde_core::{
    headers::{DesignModelHeader, LabelledArcWithPorts},
    DesignModel, StandaloneIdentificationModule,
};
use serde::Serialize;

#[derive(Debug, PartialEq, Eq, Serialize)]
pub struct SimulinkReactiveDesignModel {
    pub processes: HashSet<String>,
    pub processes_sizes: HashMap<String, u32>,
    pub delays: HashSet<String>,
    pub delays_sizes: HashMap<String, u32>,
    pub sources: HashSet<String>,
    pub sources_sizes: HashMap<String, u32>,
    pub sources_periods_numen: HashMap<String, i32>,
    pub sources_periods_denom: HashMap<String, i32>,
    pub constants: HashSet<String>,
    pub sinks: HashSet<String>,
    pub sinks_sizes: HashMap<String, u32>,
    pub sinks_deadlines_numen: HashMap<String, i32>,
    pub sinks_deadlines_denom: HashMap<String, i32>,
    pub processes_operations: HashMap<String, HashMap<String, HashMap<String, u32>>>,
    pub delays_operations: HashMap<String, HashMap<String, HashMap<String, u32>>>,
    pub links_src: Vec<String>,
    pub links_dst: Vec<String>,
    pub links_src_port: Vec<String>,
    pub links_dst_port: Vec<String>,
    pub links_size: Vec<u32>,
}

impl DesignModel for SimulinkReactiveDesignModel {
    fn unique_identifier(&self) -> String {
        "SimulinkReactiveDesignModel".to_string()
    }

    fn header(&self) -> idesyde_core::headers::DesignModelHeader {
        let mut elems: HashSet<String> = HashSet::new();
        let mut rels: HashSet<LabelledArcWithPorts> = HashSet::new();
        elems.extend(self.processes.iter().map(|x| x.to_owned()));
        elems.extend(self.delays.iter().map(|x| x.to_owned()));
        elems.extend(self.sources.iter().map(|x| x.to_owned()));
        elems.extend(self.constants.iter().map(|x| x.to_owned()));
        elems.extend(self.sinks.iter().map(|x| x.to_owned()));
        for i in 0..self.links_src.len() {
            rels.insert(LabelledArcWithPorts {
                src: self.links_src[i].to_owned(),
                dst: self.links_dst[i].to_owned(),
                src_port: Some(self.links_src_port[i].to_owned()),
                dst_port: Some(self.links_dst_port[i].to_owned()),
                label: Some(format!("{}", self.links_size[i])),
            });
        }
        DesignModelHeader {
            category: self.unique_identifier(),
            model_paths: Vec::new(),
            elements: elems.into_iter().collect(),
            relations: rels.into_iter().collect(),
        }
    }
}

struct MatlabIdentificationModule {}

impl StandaloneIdentificationModule for MatlabIdentificationModule {
    fn uid(&self) -> String {
        "MatlabIdentificationModule".to_string()
    }

    fn read_design_model(
        &self,
        path: &std::path::Path,
    ) -> Option<Box<dyn idesyde_core::DesignModel>> {
        match path.extension().and_then(|x| x.to_str()) {
            Some("slx") => {
                Command::new("matlab")
                    .arg("-nosplash")
                    .arg("-batch")
                    .arg(format!(
                        r#"s = load("{}"); m = extract_from_subsystem(s); disp(jsonencode(m));"#,
                        path.display()
                    ))
                    .output();
                None
            }
            _ => None,
        }
    }

    fn write_design_model(
        &self,
        _design_model: &Box<dyn idesyde_core::DesignModel>,
        _dest: &std::path::Path,
    ) -> bool {
        false
    }

    fn decision_header_to_model(
        &self,
        header: &idesyde_core::headers::DecisionModelHeader,
    ) -> Option<Box<dyn idesyde_core::DecisionModel>> {
        todo!()
    }

    fn identification_rules(&self) -> Vec<idesyde_core::MarkedIdentificationRule> {
        todo!()
    }

    fn reverse_identification_rules(&self) -> Vec<idesyde_core::ReverseIdentificationRule> {
        todo!()
    }
}
fn main() {
    let extract_script = if cfg!(target_os = "windows") {
        include_str!["..\\extract_from_subsystem.m"]
    } else {
        include_str!["../extract_from_subsystem.m"]
    };
    if let Ok(_) = std::fs::write("extract_from_subsystem.m", extract_script) {
        execute_standalone_identification_module(MatlabIdentificationModule {});
    };
    std::fs::remove_file("extract_from_subsystem.m");
}

use std::{collections::HashSet, f32::consts::E, net::TcpStream, sync::Arc};

use idesyde_core::{
    DecisionModel, DesignModel, IdentificationIterator, Module, OpaqueDecisionModel,
    OpaqueDesignModel,
};

use log::debug;
use tungstenite::WebSocket;

// impl HttpServerLike for ExternalServerIdentificationModule {
//     fn get_client(&self) -> Arc<reqwest::blocking::Client> {
//         self.client.clone()
//     }

//     fn get_address(&self) -> std::net::IpAddr {
//         self.address.to_owned()
//     }

//     fn get_port(&self) -> usize {
//         self.port
//     }
// }

pub struct ExternalServerIdentifiticationIterator {
    design_models: HashSet<Arc<dyn DesignModel>>,
    decision_models: HashSet<Arc<dyn DecisionModel>>,
    decision_models_to_upload: HashSet<Arc<dyn DecisionModel>>,
    design_models_to_upload: HashSet<Arc<dyn DesignModel>>,
    websocket: WebSocket<TcpStream>,
    messages: Vec<String>,
    requesting: bool,
}

impl ExternalServerIdentifiticationIterator {
    pub fn new(
        design_models: &HashSet<Arc<dyn DesignModel>>,
        decision_models: &HashSet<Arc<dyn DecisionModel>>,
        websocket: WebSocket<TcpStream>,
    ) -> ExternalServerIdentifiticationIterator {
        ExternalServerIdentifiticationIterator {
            design_models: design_models.to_owned(),
            decision_models: decision_models.to_owned(),
            decision_models_to_upload: decision_models.to_owned(),
            design_models_to_upload: design_models.to_owned(),
            websocket,
            messages: vec![],
            requesting: false,
        }
    }
}

impl Iterator for ExternalServerIdentifiticationIterator {
    type Item = Arc<dyn DecisionModel>;

    fn next(&mut self) -> Option<Self::Item> {
        // send the decision models
        if !self.requesting {
            for m in &self.decision_models_to_upload {
                if let Ok(decision_cbor) = OpaqueDecisionModel::from(m).to_json() {
                    if let Err(e) = self
                        .websocket
                        .send(tungstenite::Message::text(decision_cbor))
                    {
                        debug!("Decision CBOR upload error {}", e.to_string());
                    }
                }
            }
            self.decision_models
                .extend(self.decision_models_to_upload.drain());
            // same for design models
            for m in &self.design_models_to_upload {
                if let Ok(design_cbor) = OpaqueDesignModel::from(m.as_ref()).to_json() {
                    if let Err(e) = self.websocket.send(tungstenite::Message::text(design_cbor)) {
                        debug!("Design CBOR upload error {}", e.to_string());
                    };
                };
            }
            self.design_models
                .extend(self.design_models_to_upload.drain());
            if let Err(e) = self
                .websocket
                .send(tungstenite::Message::Text("done".to_string()))
            {
                debug!("Failed to send 'done': {}", e.to_string());
            };
            self.requesting = true;
        }
        while let Ok(message) = self.websocket.read() {
            if let Err(e) = self.websocket.flush() {
                debug!(
                    "Error found while flushing identificatio websocket: {}",
                    e.to_string()
                );
            }
            // besides the answer, also read the module's messages
            match message {
                tungstenite::Message::Text(txt_msg) => {
                    if txt_msg.eq_ignore_ascii_case("done") {
                        self.requesting = false;
                        return None;
                    } else if let Ok(opaque) = OpaqueDecisionModel::from_json_str(txt_msg.as_str())
                    {
                        let opaquea = Arc::new(opaque);
                        self.decision_models.insert(opaquea.to_owned());
                        return Some(opaquea);
                    } else {
                        // debug!("Message: {}", txt_msg.as_str());
                        self.messages.push(txt_msg);
                    }
                }
                tungstenite::Message::Binary(decision_cbor) => {
                    if let Ok(opaque) = OpaqueDecisionModel::from_cbor(decision_cbor.as_slice()) {
                        let opaquea = Arc::new(opaque);
                        self.decision_models.insert(opaquea.to_owned());
                        return Some(opaquea);
                    }
                }
                tungstenite::Message::Ping(_) => {
                    if let Err(_) = self.websocket.send(tungstenite::Message::Pong(vec![])) {
                        debug!(
                            "Failed to send ping message to other end. Trying to proceed anyway."
                        );
                    };
                }
                tungstenite::Message::Pong(_) => {
                    if let Err(_) = self.websocket.send(tungstenite::Message::Ping(vec![])) {
                        debug!(
                            "Failed to send pong message to other end. Trying to proceed anyway."
                        );
                    };
                }
                tungstenite::Message::Close(_) => return None,
                _ => (),
            }
        }
        None
    }
}

impl IdentificationIterator for ExternalServerIdentifiticationIterator {
    fn next_with_models(
        &mut self,
        decision_models: &HashSet<Arc<dyn DecisionModel>>,
        design_models: &HashSet<Arc<dyn DesignModel>>,
    ) -> Option<Arc<dyn DecisionModel>> {
        self.decision_models_to_upload.extend(
            decision_models
                .iter()
                .filter(|&x| !self.decision_models.contains(x))
                .map(|x| x.to_owned()),
        );
        self.design_models_to_upload.extend(
            design_models
                .iter()
                .filter(|&x| !self.design_models.contains(x))
                .map(|x| x.to_owned()),
        );
        return self.next();
    }

    fn collect_messages(&mut self) -> Vec<(String, String)> {
        self.messages
            .iter()
            .map(|msg| ("DEBUG".to_owned(), msg.to_owned()))
            .collect()
    }
}

pub fn identification_procedure(
    imodules: &HashSet<Arc<dyn Module>>,
    design_models: &HashSet<Arc<dyn DesignModel>>,
    pre_identified: &HashSet<Arc<dyn DecisionModel>>,
    starting_iter: i32,
) -> (HashSet<Arc<dyn DecisionModel>>, HashSet<(String, String)>) {
    let mut step = starting_iter;
    let mut fix_point = false;
    let mut identified: HashSet<Arc<dyn DecisionModel>> = HashSet::new();
    let mut messages: HashSet<(String, String)> = HashSet::new();
    let mut iterators: Vec<Box<dyn IdentificationIterator>> = imodules
        .iter()
        .map(|imodule| imodule.start_identification(design_models, &identified))
        .collect();
    identified.extend(pre_identified.iter().map(|x| x.to_owned()));
    while !fix_point {
        // try to eliminate opaque repeated decision models
        // while let Some((idx, _)) = identified
        //     .iter()
        //     .enumerate()
        //     .filter(|(_, m)| m.downcast_ref::<OpaqueDecisionModel>().is_some())
        //     .find(|(_, opaque)| {
        //         identified
        //             .iter()
        //             .any(|x| &x == opaque && x.downcast_ref::<OpaqueDecisionModel>().is_none())
        //     })
        // {
        //     identified.remove(idx);
        // }
        // the step condition forces the procedure to go at least one more, fundamental for incrementability
        fix_point = true;
        // let before = identified.len();
        let identified_step: HashSet<Arc<dyn DecisionModel>> = iterators
            .iter_mut()
            .flat_map(|iter| iter.next_with_models(&identified, design_models))
            .collect();
        // .reduce(
        //     || (vec![], HashSet::new()),
        //     |(mut m1, mut e1), (m2, e2)| {
        //         m1.extend(m2);
        //         e1.extend(e2);
        //         (m1, e1)
        //     },
        // );
        for m in identified_step {
            if !identified.contains(&m) {
                identified.insert(m);
                fix_point = false;
            }
        }
        let ident_messages: HashSet<(String, String)> = iterators
            .iter_mut()
            .flat_map(|iter| iter.collect_messages())
            .collect();
        for (lvl, msg) in ident_messages {
            if lvl.contains("DEBUG") || lvl.contains("debug") {
                debug!("{}", msg);
            }
            messages.insert((lvl, msg));
        }
        // .filter(|potential| !identified.contains(potential))
        // .collect();
        // this contain check is done again because there might be imodules that identify the same decision model,
        // and since the filtering before is step-based, it would add both identical decision models.
        // This new for-if fixes this by checking every model of this step.
        debug!(
            "{} total decision models identified at step {}",
            identified.len(),
            step
        );
        // fix_point = fix_point && (identified.len() == before);
        step += 1;
    }
    (identified, messages)
}

// #[derive(Debug, PartialEq, Eq, Hash)]
// pub struct ExternalIdentificationModule {
//     pub command_path_: PathBuf,
//     pub inputs_path_: PathBuf,
//     pub identified_path_: PathBuf,
//     pub solved_path_: PathBuf,
//     pub reverse_path_: PathBuf,
//     pub output_path_: PathBuf,
// }

// impl ExternalIdentificationModule {
//     pub fn is_java(&self) -> bool {
//         self.command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false)
//     }
// }

// impl IdentificationModule for ExternalIdentificationModule {
//     fn unique_identifier(&self) -> String {
//         self.command_path_.to_str().unwrap().to_string()
//     }

//     fn identification_step(
//         &self,
//         iteration: i32,
//         _design_models: &Vec<Arc<dyn DesignModel>>,
//         _decision_models: &Vec<Arc<dyn DecisionModel>>,
//     ) -> IdentificationResult {
//         let is_java = self
//             .command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false);
//         let output = match is_java {
//             true => std::process::Command::new("java")
//                 .arg("-jar")
//                 .arg(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .arg("-t")
//                 .arg(iteration.to_string())
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//             false => std::process::Command::new(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-i")
//                 .arg(&self.identified_path_)
//                 .arg("-t")
//                 .arg(iteration.to_string())
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//         };
//         if let Ok(out) = output {
//             if let Ok(s) = String::from_utf8(out.stdout) {
//                 let identified: Vec<Arc<dyn DecisionModel>> = s
//                     .lines()
//                     .flat_map(|p| {
//                         if let Ok(b) = std::fs::read(p) {
//                             if let Ok(header) = rmp_serde::from_slice::<DecisionModelHeader>(b.as_slice()) {
//                                 return Some(Arc::new(header) as Arc<dyn DecisionModel>);
//                             } else {
//                                 warn!("Failed to deserialize header coming from {}. Check this module for correctness.", self.unique_identifier());
//                             }
//                         } else {
//                             warn!("Unexpected header file from module {}. Check this module for correctness.", self.unique_identifier())
//                         }
//                         None
//                     })
//                     .collect();
//                 return (identified, HashSet::new());
//             }
//         }
//         (Vec::new(), HashSet::new())
//     }

//     fn reverse_identification(
//         &self,
//         _decision_model: &Vec<Arc<dyn DecisionModel>>,
//         _design_model: &Vec<Arc<dyn DesignModel>>,
//     ) -> Vec<Arc<dyn DesignModel>> {
//         let is_java = self
//             .command_path_
//             .extension()
//             .and_then(|s| s.to_str())
//             .map(|s| s == "jar")
//             .unwrap_or(false);
//         let output = match is_java {
//             true => std::process::Command::new("java")
//                 .arg("-jar")
//                 .arg(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-s")
//                 .arg(&self.solved_path_)
//                 .arg("-r")
//                 .arg(&self.reverse_path_)
//                 .arg("-o")
//                 .arg(&self.output_path_)
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//             false => std::process::Command::new(&self.command_path_)
//                 .arg("-m")
//                 .arg(&self.inputs_path_)
//                 .arg("-s")
//                 .arg(&self.solved_path_)
//                 .arg("-r")
//                 .arg(&self.reverse_path_)
//                 .arg("-o")
//                 .arg(&self.output_path_)
//                 .stdout(Stdio::piped())
//                 .stderr(Stdio::piped())
//                 .output(),
//         };
//         if let Ok(out) = output {
//             if let Ok(s) = String::from_utf8(out.stdout) {
//                 let reversed: Vec<Arc<dyn DesignModel>> = s
//                     .lines()
//                     .map(|p| {
//                         let b = std::fs::read(p)
//                             .expect("Failed to read header file from disk during identification");
//                         let header = rmp_serde::from_slice::<DesignModelHeader>(b.as_slice())
//                             .expect(
//                             "Failed to deserialize header file from disk during identification.",
//                         );
//                         Arc::new(header) as Arc<dyn DesignModel>
//                     })
//                     .collect();
//                 return reversed;
//             }
//         }
//         Vec::new()
//     }
// }

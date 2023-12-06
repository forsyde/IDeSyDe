use std::{
    collections::HashSet,
    f32::consts::E,
    net::TcpStream,
    ops::Index,
    sync::{Arc, Mutex},
    time::Duration,
};

use idesyde_core::{
    DecisionModel, DesignModel, IdentificationIterator, IdentificationResult, Module,
    OpaqueDecisionModel, OpaqueDesignModel,
};

use log::debug;
use tungstenite::WebSocket;

use rayon::prelude::*;

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
    design_models: Vec<Arc<dyn DesignModel>>,
    decision_models: Vec<Arc<dyn DecisionModel>>,
    decision_models_to_upload: Vec<Arc<dyn DecisionModel>>,
    design_models_to_upload: Vec<Arc<dyn DesignModel>>,
    websocket: WebSocket<TcpStream>,
    messages: Vec<String>,
}

impl ExternalServerIdentifiticationIterator {
    pub fn new(
        design_models: &Vec<Arc<dyn DesignModel>>,
        decision_models: &Vec<Arc<dyn DecisionModel>>,
        websocket: WebSocket<TcpStream>,
    ) -> ExternalServerIdentifiticationIterator {
        ExternalServerIdentifiticationIterator {
            design_models: design_models.to_owned(),
            decision_models: decision_models.to_owned(),
            decision_models_to_upload: decision_models.to_owned(),
            design_models_to_upload: design_models.to_owned(),
            websocket,
            messages: vec![],
        }
    }
}

impl Iterator for ExternalServerIdentifiticationIterator {
    type Item = IdentificationResult;

    fn next(&mut self) -> Option<Self::Item> {
        // if the websocket is closed, we short-curcuit the function.
        if !self.websocket.can_read() {
            return None;
        }
        // send the decision models
        for m in &self.decision_models_to_upload {
            if let Ok(decision_cbor) = OpaqueDecisionModel::from(m).to_json() {
                // println!("Uploading decision model {}", m.category());
                if let Err(e) = self
                    .websocket
                    .send(tungstenite::Message::text(decision_cbor))
                {
                    debug!("Decision CBOR upload error {}", e.to_string());
                }
            }
        }
        self.decision_models.extend(
            self.decision_models_to_upload
                .drain(0..self.decision_models_to_upload.len()),
        );
        // same for design models
        for m in &self.design_models_to_upload {
            if let Ok(design_cbor) = OpaqueDesignModel::from(m.as_ref()).to_json() {
                // println!("Uploading design model {}", m.category());
                if let Err(e) = self.websocket.send(tungstenite::Message::text(design_cbor)) {
                    debug!("Design CBOR upload error {}", e.to_string());
                };
            };
        }
        self.design_models.extend(
            self.design_models_to_upload
                .drain(0..self.design_models_to_upload.len()),
        );
        if let Err(e) = self.websocket.send(tungstenite::Message::text("done")) {
            debug!("Failed to send 'done': {}", e.to_string());
        };
        // println!("send done");
        while let Ok(message) = self.websocket.read() {
            // besides the answer, also read the module's messages
            match message {
                tungstenite::Message::Text(txt_msg) => {
                    if txt_msg.eq_ignore_ascii_case("done") {
                        return Some((
                            self.decision_models.clone(),
                            self.messages.drain(0..self.messages.len()).collect(),
                        ));
                    } else if let Ok(opaque) = OpaqueDecisionModel::from_json_str(txt_msg.as_str())
                    {
                        let opaquea = Arc::new(opaque) as Arc<dyn DecisionModel>;
                        if !self.decision_models.contains(&opaquea) {
                            self.decision_models.push(opaquea.to_owned());
                        }
                    } else {
                        // debug!("Message: {}", txt_msg.as_str());
                        self.messages.push(txt_msg);
                    }
                }
                tungstenite::Message::Binary(decision_cbor) => {
                    if let Ok(opaque) = OpaqueDecisionModel::from_cbor(decision_cbor.as_slice()) {
                        let opaquea = Arc::new(opaque) as Arc<dyn DecisionModel>;
                        if !self.decision_models.contains(&opaquea) {
                            self.decision_models.push(opaquea.to_owned());
                        }
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
        decision_models: &Vec<Arc<dyn DecisionModel>>,
        design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> Option<IdentificationResult> {
        self.decision_models_to_upload.extend(
            decision_models
                .iter()
                .filter(|&m| {
                    !self.decision_models.iter().any(|x| {
                        x.partial_cmp(m) == Some(std::cmp::Ordering::Greater)
                            || x.partial_cmp(m) == Some(std::cmp::Ordering::Equal)
                    })
                })
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

    // fn collect_messages(&mut self) -> Vec<(String, String)> {
    //     self.messages
    //         .iter()
    //         .map(|msg| ("DEBUG".to_owned(), msg.to_owned()))
    //         .collect()
    // }
}

impl Drop for ExternalServerIdentifiticationIterator {
    fn drop(&mut self) {
        if self.websocket.can_write() {
            if let Err(e) = self.websocket.close(None) {
                debug!(
                    "Failed to close identification websocket: {}",
                    e.to_string()
                );
            }
        }
    }
}

pub fn identification_procedure(
    imodules: &Vec<Arc<dyn Module>>,
    design_models: &Vec<Arc<dyn DesignModel>>,
    pre_identified: &Vec<Arc<dyn DecisionModel>>,
    starting_iter: i32,
) -> (Vec<Arc<dyn DecisionModel>>, Vec<(String, String)>) {
    let mut step = starting_iter;
    let mut identified: Vec<Arc<dyn DecisionModel>> = pre_identified.clone();
    let mut messages: Vec<(String, String)> = Vec::new();
    let mut fix_point = false;
    // let mut iterators: Vec<Box<dyn IdentificationIterator>> = imodules
    //     .iter()
    //     .map(|imodule| imodule.identification_step(design_models, &identified))
    //     .collect();
    while !fix_point {
        fix_point = true;
        // let before = identified.len();
        // let identified_step: Vec<IdentificationResult> = (0..iterators.len()).into_par_iter().map(|i| {
        //     if let Ok(mut iter) = iterators[i].lock() {
        //         iter.next_with_models(&identified, design_models)
        //     } else {
        //         None
        //     }
        // }).flatten().collect();

        let (identified_models, msgs) = imodules
            .par_iter()
            .map(|imodule| imodule.identification_step(&identified, design_models))
            .reduce_with(|(models1, msgs1), (models2, msgs2)| {
                let mut models = Vec::new();
                models.extend(models1.into_iter());
                for m in models2 {
                    if !models.contains(&m) {
                        models.push(m);
                    }
                }
                // models.extend(models2.into_iter().filter(|m| !models.contains(m)));
                let mut msgs = Vec::new();
                msgs.extend(msgs1.into_iter());
                for msg in msgs2 {
                    if !msgs.contains(&msg) {
                        msgs.push(msg);
                    }
                }
                (models, msgs)
            })
            .unwrap_or((vec![], vec![]));
        // add completely new models or replace opaque deicion mdoels for non-opaque ones
        for m in &identified_models {
            if let Some(previous_idx) = identified.iter().position(|x| {
                (x.partial_cmp(m) == Some(std::cmp::Ordering::Less)
                    || x.partial_cmp(m) == Some(std::cmp::Ordering::Equal))
                    && x.downcast_ref::<OpaqueDecisionModel>().is_some()
                    && m.downcast_ref::<OpaqueDecisionModel>().is_none()
            }) {
                // debug!("Replaced {}", identified[previous_idx].category());
                identified.remove(previous_idx);
                identified.push(m.to_owned());
                fix_point = false;
            } else if !identified.iter().any(|x| {
                x.partial_cmp(m) == Some(std::cmp::Ordering::Greater)
                    || x.partial_cmp(m) == Some(std::cmp::Ordering::Equal)
            }) {
                // debug!("added {}", m.category());
                identified.push(m.to_owned());
                fix_point = false;
            };
        }
        for msg in msgs {
            debug!("{}", msg);
            messages.push(("DEBUG".to_string(), msg.to_owned()));
        }
        // let ident_messages: HashSet<(String, String)> = iterators
        //     .iter_mut()
        //     .flat_map(|iter| iter.collect_messages())
        //     .collect();
        // for (lvl, msg) in ident_messages {
        // }
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

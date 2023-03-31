use std::{fs, path::Path};

use idesyde_rust_core::IdentificationModule;

pub mod orchestration;

fn main() {
    let imodules =
        orchestration::initiate_identification_modules(Path::new("imodules"), Path::new("run"));
    println!("finding");
    std::fs::create_dir_all(Path::new("run").join("inputs").join("json"));
    std::fs::create_dir_all(Path::new("run").join("inputs").join("msgpack"));
    std::fs::create_dir_all(Path::new("run").join("identified").join("json"));
    std::fs::create_dir_all(Path::new("run").join("identified").join("msgpack"));
    for imodule in &imodules {
        imodule.identification_step(0);
    }
    std::thread::sleep(std::time::Duration::from_secs(1));
    for imodule in &imodules {
        imodule.identification_step(-1);
    }
    println!("done");
}

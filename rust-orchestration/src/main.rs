use std::{path::Path, fs};

use idesyde_rust_core::IdentificationModule;

pub mod orchestration;

fn main() {
    let imodules = orchestration::find_external_identification_modules(
        Path::new("imodules"),
        Path::new("run"),
    );
    println!("finding");
    std::fs::create_dir_all(Path::new("run").join("inputs").join("json"));
    std::fs::create_dir_all(Path::new("run").join("inputs").join("msgpack"));
    std::fs::create_dir_all(Path::new("run").join("identified").join("json"));
    std::fs::create_dir_all(Path::new("run").join("identified").join("msgpack"));
    for mut imodule in imodules {
        let mut res = imodule.identification_step(0);
    }
    println!("done");
}

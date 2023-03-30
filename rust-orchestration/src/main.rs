use std::path::Path;

use idesyde_rust_core::IdentificationModule;

pub mod orchestration;

fn main() {
    let imodules = orchestration::find_external_identification_modules(
        Path::new("imodules"),
        Path::new("run"),
    );
    println!("finding");
    for imodule in imodules {
        println!("found {}", imodule.unique_identifier());
    }
    println!("done");
}

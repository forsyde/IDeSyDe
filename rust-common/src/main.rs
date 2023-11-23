use idesyde_blueprints::execute_standalone_module;
use idesyde_common::make_common_module;

fn main() {
    execute_standalone_module(make_common_module());
}

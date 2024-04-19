use autocxx::prelude::*;
use idesyde_common::models::AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL;
use idesyde_core::ExplorationSolution; // use all the main autocxx functions

include_cpp! {
    #include "ortools/base/logging.h"
    #include "ortools/sat/cp_model.h"
    #include "ortools/sat/cp_model.pb.h"
    #include "ortools/sat/cp_model_solver.h"
    #include "ortools/util/sorted_interval_list.h"
    safety!(unsafe)
    generate!("operations_research::sat::CpModelBuilder") // add this line for each function or type you wish to generate
}

pub fn solve_aad2pmmmap(
    m: &AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL,
) -> impl Iterator<Item = ExplorationSolution> {
    let mut cp_model = UniquePtr::emplace(ffi::operations_research::sat::CpModelBuilder::new());
    std::iter::empty()
}

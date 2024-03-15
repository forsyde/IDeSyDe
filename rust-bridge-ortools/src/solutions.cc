#include "rust-bridge-ortools/include/solutions.hh"
#include "rust-bridge-ortools/src/lib.rs.h"

void prepare_workload_dse_model(WorkloadDSEInput const &input) {

  using namespace operations_research;
  sat::CpModelBuilder cp_model;

  const IntVar x = cp_model.NewIntVar().WithName("x");
  // const IntVar y = cp_model.NewIntVar(domain).WithName("y");
  // const IntVar z = cp_model.NewIntVar(domain).WithName("z");
  //
  // cp_model.AddNotEqual(x, y);
  //
  // Model model;
  //
  // int num_solutions = 0;
  // model.Add(NewFeasibleSolutionObserver([&](const CpSolverResponse& r) {
  //   LOG(INFO) << "Solution " << num_solutions;
  //   LOG(INFO) << "  x = " << SolutionIntegerValue(r, x);
  //   LOG(INFO) << "  y = " << SolutionIntegerValue(r, y);
  //   LOG(INFO) << "  z = " << SolutionIntegerValue(r, z);
  //   num_solutions++;
  // }));
  //
  // // Tell the solver to enumerate all solutions.
  // SatParameters parameters;
  // parameters.set_enumerate_all_solutions(true);
  // model.Add(NewSatParameters(parameters));
  // const CpSolverResponse response = SolveCpModel(cp_model.Build(), &model);
  //
  // LOG(INFO) << "Number of solutions found: " << num_solutions;
}

int main() {
  // operations_research::sat::SearchAllSolutionsSampleSat();

  return 0;
}

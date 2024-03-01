#pragma once
#include <stdint.h>
#include <stdlib.h>

#include <algorithm>

#include "ortools/base/logging.h"
#include "ortools/sat/cp_model.h"
#include "ortools/sat/cp_model.pb.h"
#include "ortools/sat/cp_model_solver.h"
#include "ortools/util/sorted_interval_list.h"

struct WorkloadDSEInput;

void prepare_workload_dse_model(
  WorkloadDSEInput const& input
);


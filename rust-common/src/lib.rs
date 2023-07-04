use std::collections::HashMap;

use idesyde_core::{DecisionModel, DesignModel};
use models::{PartitionedTiledMulticore, RuntimesAndProcessors, SDFApplication, TiledMultiCore};
use petgraph::{algo::connected_components, Graph};

pub mod irules;
pub mod models;

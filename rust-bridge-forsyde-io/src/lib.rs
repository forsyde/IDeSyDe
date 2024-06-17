use forsyde_io_core::{SystemGraph, Vertex};
use forsyde_io_libforsyde::ForSyDeHierarchy;
use idesyde_common::models::AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL;
use idesyde_core::{DesignModel, RustEmbeddedModule};

pub struct ForSyDeIODesignModel {
    system_graph: SystemGraph,
}

fn inner_reverse_identify_aperiodic_asynchronous_dataflow_to_partitioned_memory_mappable_multicore_and_pl(
    solved_models: &[AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticoreAndPL],
    design_models: &[ForSyDeIODesignModel],
) -> impl Iterator<Item = dyn DesignModel> {
    solved_models
        .iter()
        .map(|model| {
            let num_jobs: usize = model
                .aperiodic_asynchronous_dataflows
                .iter()
                .map(|app| app.job_graph_name.len())
                .sum();

            let mut reversed_system_graph = SystemGraph::new();
            // let mut job_graph = DefaultDirectedGraph::<(String, u64), DefaultEdge>::new();
            // let mut job_mapping = HashMap::<(String, u64), String>::new();
            // let mut job_ordering = HashMap::<(String, u64), usize>::new();

            // for app in model.aperiodic_asynchronous_dataflows {
            //     for i in 0..app.job_graph_src_name.len() {
            //         let src = (app.job_graph_src_name[i].clone(), app.job_graph_src_instance()[i]);
            //         let dst = (app.job_graph_dst_name[i].clone(), app.job_graph_dst_instance()[i]);
            //         job_graph.add_vertex(src.clone());
            //         job_graph.add_vertex(dst.clone());
            //         job_graph.add_edge(src, dst);
            //     }
            // }

            model.processes_to_memory_mapping.iter().for_each(|(process, mem)| {
                let proc_vertex = Vertex::new(process);
                let proc_idx = reversed_system_graph.add_node(proc_vertex);
                let mem_vertex = Vertex::new(mem);
                let mem_idx = reversed_system_graph.add_node(mem_vertex);
                let mem_mapped = ForSyDeHierarchy::MemoryMapped
                    .enforce(&reversed_system_graph, proc_vertex);
                mem_mapped.mapping_host(
                    ForSyDeHierarchy::GenericMemoryModule
                        .enforce(&reversed_system_graph, mem_vertex),
                );
                ForSyDeHierarchy::GreyBox
                    .enforce(&reversed_system_graph, mem_vertex)
                    .add_contained(ForSyDeHierarchy::Visualizable.enforce(mem_mapped));
            });

            model.buffer_to_memory_mappings().iter().for_each(|(buf, mem)| {
                let buf_vertex = reversed_system_graph.new_vertex(buf.clone());
                let mem_vertex = reversed_system_graph.new_vertex(mem.clone());
                let mem_mapped = ForSyDeHierarchy::MemoryMapped
                    .enforce(&reversed_system_graph, buf_vertex);
                mem_mapped.mapping_host(
                    ForSyDeHierarchy::GenericMemoryModule
                        .enforce(&reversed_system_graph, mem_vertex),
                );
                ForSyDeHierarchy::GreyBox
                    .enforce(&reversed_system_graph, mem_vertex)
                    .add_contained(ForSyDeHierarchy::Visualizable.enforce(mem_mapped));
            });

            model.processes_to_runtime_scheduling().iter().for_each(|(process, sched)| {
                for app in model.aperiodic_asynchronous_dataflows {
                    for i in 0..app.job_graph_src_name.len() {
                        if process == &app.job_graph_src_name[i] {
                            let src = (app.job_graph_src_name[i].clone(), app.job_graph_src_instance()[i]);
                            job_mapping.insert(src.clone(), sched.clone());
                        }
                    }
                }
                let proc_vertex = reversed_system_graph.new_vertex(process.clone());
                let sched_vertex = reversed_system_graph.new_vertex(sched.clone());
                let scheduled = ForSyDeHierarchy::Scheduled
                    .enforce(&reversed_system_graph, proc_vertex);
                scheduled.runtime_host(
                    ForSyDeHierarchy::AbstractRuntime
                        .enforce(&reversed_system_graph, sched_vertex),
                );
                ForSyDeHierarchy::GreyBox
                    .enforce(&reversed_system_graph, sched_vertex)
                    .add_contained(ForSyDeHierarchy::Visualizable.enforce(scheduled));
            });

            model.processes_to_logic_programmable_areas().iter().for_each(|(process, pla)| {
                for app in model.aperiodic_asynchronous_dataflows {
                    for i in 0..app.job_graph_src_name.len() {
                        if process == &app.job_graph_src_name[i] {
                            let src = (app.job_graph_src_name[i].clone(), app.job_graph_src_instance()[i]);
                            job_mapping.insert(src.clone(), pla.clone());
                        }
                    }
                }
                let proc_vertex = reversed_system_graph.new_vertex(process.clone());
                let pla_vertex = reversed_system_graph.new_vertex(pla.clone());
                ForSyDeHierarchy::LogicProgrammableAreaMapped
                    .enforce(&reversed_system_graph, proc_vertex)
                    .host_logic_programmable_area(
                        ForSyDeHierarchy::LogicProgrammableModule
                            .enforce(&reversed_system_graph, pla_vertex),
                    );
                ForSyDeHierarchy::GreyBox
                    .enforce(&reversed_system_graph, pla_vertex)
                    .add_contained(ForSyDeHierarchy::Visualizable.enforce(proc_vertex));
            });

            let mut current_instances = HashMap::<String, u64>::new();
            for app in model.aperiodic_asynchronous_dataflows {
                for process in app.processes() {
                    current_instances.insert(process.clone(), 0);
                }
            }

            for app in model.aperiodic_asynchronous_dataflows {
                for process in app.processes() {
                    current_instances.insert(process.clone(), 0);
                }
            }

            model.super_loop_schedules().iter().for_each(|(sched, looplist)| {
                for (idx, entry) in looplist.iter().enumerate() {
                    let instance = current_instances.get(entry).cloned().unwrap_or_default();
                    job_ordering.insert((entry.clone(), instance), idx);
                }
                let sched_vertex = reversed_system_graph.new_vertex(sched.clone());
                let scheduler = ForSyDeHierarchy::SuperLoopRuntime
                    .enforce(&reversed_system_graph, sched_vertex);
                scheduler.super_loop_entries(looplist.clone());
            });

            let recomputed_times = recompute_execution_times(model);
            let maximum_cycles = recompute_maximum_cycles(
                &job_graph,
                &job_mapping,
                &job_ordering,
                &recomputed_times,
                &HashMap::new(),
            );

            let mut original_sdf_channels = HashMap::<String, BufferLike>::new();
            design_models
                .iter()
                .flat_map(|x| ForSyDeIODesignModel::try_from(x).into_iter())
                .for_each(|x| {
                    let sg = x.system_graph();
                    sg.vertex_set()
                        .iter()
                        .flat_map(|v| ForSyDeHierarchy::BufferLike::try_view(&sg, v).into_iter())
                        .for_each(|c| {
                            original_sdf_channels.insert(c.get_identifier().clone(), c.clone());
                        });
                });

            model.aperiodic_asynchronous_dataflows.iter().for_each(|app| {
                let max_app_cycle = maximum_cycles
                    .iter()
                    .filter(|(process, _)| app.processes().contains(&process.0))
                    .map(|(_, cycles)| *cycles)
                    .max()
                    .unwrap_or_default();
                let inv_max_app_cycle = 1.0 / max_app_cycle;
                let mut scale = 1.0;
                while inv_max_app_cycle * scale < 0.1 {
                    scale *= 10.0;
                }
                for actor in app.processes() {
                    let repetitions = maximum_cycles
                        .iter()
                        .filter(|(process, _)| process.0 == actor)
                        .map(|(_, instance)| *instance)
                        .max()
                        .unwrap_or(1);
                    let process = reversed_system_graph.new_vertex(actor.clone());
                    let behaviour = ForSyDeHierarchy::AnalyzedBehavior
                        .enforce(&reversed_system_graph, process);
                    behaviour.throughput_in_secs_denominator(scale.round() as u64);
                    behaviour.throughput_in_secs_numerator(
                        repetitions * (inv_max_app_cycle * scale).round() as u64,
                    );
                }
                app.buffers().iter().for_each(|channel| {
                    let channel_vec = reversed_system_graph.new_vertex(channel.clone());
                    let bbuf = ForSyDeHierarchy::BoundedBufferLike
                        .enforce(&reversed_system_graph, channel_vec);
                    let channel_buf = ForSyDeHierarchy::SDFChannel.enforce(bbuf.clone());
                    let consumer = app
                        .processes()
                        .iter()
                        .find(|p| app.process_get_from_buffer_in_bits().get(p).contains_key(channel))
                        .cloned()
                        .unwrap_or_default();
                    let consumer_max_instance = app
                        .job_graph_name()
                        .iter()
                        .enumerate()
                        .filter(|(_, name)| **name == consumer)
                        .map(|(i, _)| app.job_graph_instance()[i])
                        .max()
                        .unwrap_or(1);
                    let max_size = app
                        .process_get_from_buffer_in_bits()
                        .get(&consumer)
                        .and_then(|m| m.get(channel))
                        .cloned()
                        .unwrap_or_default()
                        * consumer_max_instance;
                    if let Some(original) = original_sdf_channels.get(channel) {
                        if original.element_size_in_bits() > 0 {
                            bbuf.max_elements(
                                channel_buf.num_initial_tokens()
                                    + (max_size / original.element_size_in_bits()) as usize,
                            );
                            bbuf.element_size_in_bits(original.element_size_in_bits());
                        }
                    }
                });
            });

            HashSet::new()
        })
        .collect()
}

pub fn make_module() -> RustEmbeddedModule {
    RustEmbeddedModule::builder()
        .unique_identifier("ForSyDeIORustModule".to_string())
        .identification_rules(vec![])
        .explorers(vec![])
        .build()
        .expect("Failed to build ForSyDeIORustModule. Should never happen.")
}

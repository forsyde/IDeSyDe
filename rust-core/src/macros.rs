#[macro_export]
macro_rules! opaque_to_model_gen {
    [$($x:ty),*] => {
        |m: &idesyde_core::OpaqueDecisionModel| {
            match idesyde_core::DecisionModel::category(m).as_str() {
                $(
                    stringify!($x) => {
                        idesyde_core::DecisionModel::body_as_cbor(m)
                        .and_then(|b| ciborium::from_reader::<$x, &[u8]>(b.as_slice()).ok())
                        .or_else(||
                            idesyde_core::DecisionModel::body_as_json(m)
                            .and_then(|j| serde_json::from_str::<$x>(&j).ok())
                        )
                        .or_else(||
                            idesyde_core::DecisionModel::body_as_msgpack(m)
                            .and_then(|j| rmp_serde::from_slice::<$x>(&j).ok())
                        )
                        .map(|m| std::sync::Arc::new(m) as std::sync::Arc<dyn idesyde_core::DecisionModel>)
                    },
                )*
                _ => {
                    None
                },
            }
        }
    };
}

#[macro_export]
macro_rules! decision_models_schemas_gen {
    [$($x:ty),*] => {
        HashSet::from([
            $(
                serde_json::to_string_pretty(&schema_for!($x)).unwrap(),
            )*
        ])
    };
}

#[macro_export]
macro_rules! impl_decision_model_standard_parts {
    ($x:ty) => {
        fn body_as_json(&self) -> Option<String> {
            serde_json::to_string(self).ok()
        }

        fn body_as_msgpack(&self) -> Option<Vec<u8>> {
            rmp_serde::to_vec(self).ok()
        }

        fn body_as_cbor(&self) -> Option<Vec<u8>> {
            let mut b: Vec<u8> = Vec::new();
            if let Ok(_) = ciborium::into_writer(self, &mut b) {
                Some(b)
            } else {
                None
            }
        }

        fn category(&self) -> String {
            stringify!($x).to_string()
        }
    };
}

/// This macro takes a reference to a DecisionModel trait object
/// and tries to downcast to a specific decision model.
///
/// The macro generates is smart enough to
/// _also_ decode decision models from OpaqueDecisionModel.
/// Hence, this macro is essentially a shortcut to all the means a
/// non-specific decision model can be made specific.
///
/// So, if you call:
///
///     cast_dyn_decision_model!(m ,t)
///
/// where `m` is an `&dyn DecisionModel` or equivalent, e.g. `Arc<dyn DecisionModel>`,
/// and `t` is a `DecisionModel` type, then the resulting will be `Option<Arc<t>>`.
#[macro_export]
macro_rules! cast_dyn_decision_model {
    ($b:ident,$x:ty) => {
        $b.downcast_ref::<idesyde_core::OpaqueDecisionModel>()
            .and_then(|opaque| {
                if idesyde_core::DecisionModel::category(opaque).as_str() == stringify!($x) {
                    idesyde_core::DecisionModel::body_as_cbor(opaque)
                        .and_then(|b| ciborium::from_reader::<$x, &[u8]>(b.as_slice()).ok())
                        .or_else(|| {
                            idesyde_core::DecisionModel::body_as_json(opaque)
                                .and_then(|j| serde_json::from_str::<$x>(&j).ok())
                        })
                        .or_else(|| {
                            idesyde_core::DecisionModel::body_as_msgpack(opaque)
                                .and_then(|j| rmp_serde::from_slice::<$x>(&j).ok())
                        })
                        // .map(|m| std::sync::Arc::new(m) as Arc<$x>)
                } else {
                    None as Option<$x>
                }
            })
            .or_else(|| $b.downcast_ref::<$x>().map(|x| x.to_owned()))
    };
}

#[macro_export]
macro_rules! opaque_to_model_gen {
    [$($x:ty),*] => {
        |m: &idesyde_core::OpaqueDecisionModel| {
            match idesyde_core::DecisionModel::category(m).as_str() {
                $(
                    stringify!($x) => {
                        idesyde_core::DecisionModel::body_as_json(m)
                        .and_then(|b| match serde_json::from_str::<$x>(&b) {
                            Ok(a) => {
                                Some(a)
                            },
                            Err(e) => {
                                None
                            }
                        })
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

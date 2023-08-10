#[macro_export]
macro_rules! decision_models_schemas_gen {
    ($($x:ty),*) => {
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
            "$x".to_string()
        }
    };
}

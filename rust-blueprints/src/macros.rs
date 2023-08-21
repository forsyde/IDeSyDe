#[macro_export]
macro_rules! opaque_to_model_gen {
    ($($x:ty),*) => {
        |m: &idesyde_blueprints::OpaqueDecisionModel| {
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
macro_rules! decision_message_to_model_gen {
    ($($x:ty),*) => {
        |message: &idesyde_blueprints::DecisionModelMessage| {
            match message.header().category.as_str() {
                $(
                    stringify!($x) => message.body_with_newlines_unescaped()
                                .and_then(|b| serde_json::from_str::<$x>(b.as_str()).ok())
                                .map(|m| std::sync::Arc::new(m) as std::sync::Arc<dyn idesyde_core::DecisionModel>),
                )*
                _ => None,
            }
        }
    };
}

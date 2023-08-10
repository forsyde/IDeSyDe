#[macro_export]
macro_rules! decision_header_to_model_gen {
    ($($x:ty),*) => {
        |header: &idesyde_core::headers::DecisionModelHeader| {
            header.body_path.as_ref().and_then(|bp| {
                let bpath = std::path::PathBuf::from(bp);
                match header.category.as_str() {
                    $(
                        "$x" => idesyde_core::load_decision_model::<$x>(&bpath)
                        .map(|m| std::sync::Arc::new(m) as std::sync::Arc<dyn idesyde_core::DecisionModel>),
                    )*
                    _ => None,
                }
            })
        }
    };
}

#[macro_export]
macro_rules! decision_message_to_model_gen {
    ($($x:ty),*) => {
        |message: &idesyde_blueprints::DecisionModelMessage| {
            match message.header().category.as_str() {
                $(
                    "$x" => message.body_with_newlines_unescaped()
                                .and_then(|b| serde_json::from_str::<$x>(b.as_str()).ok())
                                .map(|m| std::sync::Arc::new(m) as std::sync::Arc<dyn idesyde_core::DecisionModel>),
                )*
                _ => None,
            }
        }
    };
}

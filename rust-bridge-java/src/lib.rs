use std::{
    borrow::Borrow,
    collections::HashSet,
    io::{BufRead, Read},
    sync::Arc,
};

use idesyde_core::{
    DecisionModel, DesignModel, IdentificationResult, IdentificationRuleLike,
    MarkedIdentificationRule, Module, OpaqueDecisionModel, OpaqueDecisionModelBuilder,
    OpaqueDesignModel, ReverseIdentificationRuleLike,
};
use jars::JarOptionBuilder;
use jni::{
    objects::{JByteArray, JObject, JObjectArray, JPrimitiveArray, JString, JValue},
    AttachGuard, InitArgs, InitArgsBuilder, JNIEnv, JNIVersion, JavaVM,
};
use zip::ZipArchive;

fn design_model_to_java_opaque<'a>(
    env: &mut JNIEnv<'a>,
    m: &dyn DesignModel,
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let opaque_class = env.find_class("idesyde/core/OpaqueDesignModel")?;
    let category = env.new_string(m.category())?;
    let format = env.new_string(m.format())?;
    let body = env.new_string(
        m.body_as_string()
            .expect("Failed to get body of design model"),
    )?;
    let elems = env.new_object(set_class, "()V", &[])?;
    for s in &m.elements() {
        let java_string = env.new_string(s)?;
        env.call_method(
            &elems,
            "add",
            "(Ljava/lang/Object;)Z",
            &[JValue::Object(java_string.as_ref())],
        )?;
    }
    let obj = env.new_object(
        opaque_class,
        "(Ljava/lang/String;Ljava/util/Set;Ljava/lang/String;Ljava/lang/String;)V",
        &[
            JValue::Object(category.as_ref()),
            JValue::Object(elems.as_ref()),
            JValue::Object(format.as_ref()),
            JValue::Object(body.as_ref()),
        ],
    )?;
    Ok(obj)
}

fn java_to_rust_design_model<'a>(
    env: &mut JNIEnv<'a>,
    java_result: &JObject<'a>,
) -> Result<OpaqueDesignModel, jni::errors::Error> {
    let mut builder = OpaqueDesignModel::builder();
    let category_obj = env.call_method(java_result, "category", "()Ljava/lang/String;", &[])?;
    let category = env
        .get_string(&JString::from(category_obj.l()?))?
        .to_str()
        .map(|x| x.to_string())
        .expect("Failed to convert Java string to Rust string through UTF8 problems");
    builder.category(category);
    let format_obj = env.call_method(java_result, "format", "()Ljava/lang/String;", &[])?;
    let format = env
        .get_string(&JString::from(format_obj.l()?))?
        .to_str()
        .map(|x| x.to_string())
        .expect("Failed to convert Java string to Rust string through UTF8 problems");
    builder.format(format);
    let mut elems: HashSet<String> = HashSet::new();
    let part_array_obj =
        env.call_method(java_result, "elementsAsArray", "()[Ljava/lang/String;", &[])?;
    let elems_array = JObjectArray::from(part_array_obj.l()?);
    let elems_array_size = env.get_array_length(elems_array.borrow())?;
    for i in 0..elems_array_size {
        let elem = env.get_object_array_element(&elems_array, i)?;
        let elem_string_java = JString::from(elem);
        let rust_str = env
            .get_string(&elem_string_java)?
            .to_str()
            .map(|x| x.to_owned());
        if let Ok(elem_str) = rust_str {
            elems.insert(elem_str.to_string());
        } else {
            panic!("Failed to convert Java string to Rust string through UTF8 problems")
        }
    }
    builder.elements(elems);
    let text_body = env
        .call_method(java_result, "asString", "()Ljava/util/Optional;", &[])?
        .l()?;
    let text_is_present = env.call_method(&text_body, "isPresent", "()Z", &[])?;
    builder.body(None);
    if let Ok(true) = text_is_present.z() {
        let json_body_inner = env
            .call_method(&text_body, "get", "()Ljava/lang/Object;", &[])?
            .l()?;
        let json_body = env
            .get_string(&JString::from(json_body_inner))?
            .to_str()
            .map(|x| x.to_string());
        builder.body(json_body.ok());
    }
    Ok(builder
        .build()
        .expect("Failed to build opaque decision model. Should not happen"))
}

fn java_to_rust_decision_model<'a>(
    env: &mut JNIEnv<'a>,
    java_result: &JObject<'a>,
) -> Result<OpaqueDecisionModel, jni::errors::Error> {
    let mut builder = OpaqueDecisionModel::builder();
    let category_obj = env.call_method(java_result, "category", "()Ljava/lang/String;", &[])?;
    let category = env
        .get_string(&JString::from(category_obj.l()?))?
        .to_str()
        .map(|x| x.to_string())
        .expect("Failed to convert Java string to Rust string through UTF8 problems");
    builder.category(category);
    let mut part: HashSet<String> = HashSet::new();
    let part_array_obj =
        env.call_method(java_result, "partAsArray", "()[Ljava/lang/String;", &[])?;
    let part_array = JObjectArray::from(part_array_obj.l()?);
    let part_array_size = env.get_array_length(part_array.borrow())?;
    for i in 0..part_array_size {
        let elem = env.get_object_array_element(&part_array, i)?;
        let elem_string_java = JString::from(elem);
        let rust_str = env
            .get_string(&elem_string_java)?
            .to_str()
            .map(|x| x.to_owned());
        if let Ok(elem_str) = rust_str {
            part.insert(elem_str.to_string());
        } else {
            panic!("Failed to convert Java string to Rust string through UTF8 problems")
        }
    }
    builder.part(part);
    let json_body_obj = env
        .call_method(java_result, "asJsonString", "()Ljava/util/Optional;", &[])?
        .l()?;
    let json_is_present = env.call_method(&json_body_obj, "isPresent", "()Z", &[])?;
    builder.body_json(None);
    if let Ok(true) = json_is_present.z() {
        let json_body_inner = env
            .call_method(&json_body_obj, "get", "()Ljava/lang/Object;", &[])?
            .l()?;
        let json_body = env
            .get_string(&JString::from(json_body_inner))?
            .to_str()
            .map(|x| x.to_string());
        builder.body_json(json_body.ok());
    }
    let cbor_body_obj = env
        .call_method(java_result, "asCBORBinary", "()Ljava/util/Optional;", &[])?
        .l()?;
    let cbor_is_present = env.call_method(&cbor_body_obj, "isPresent", "()Z", &[])?;
    builder.body_cbor(None);
    if let Ok(true) = cbor_is_present.z() {
        let cbor_body_inner = env
            .call_method(&cbor_body_obj, "get", "()Ljava/lang/Object;", &[])?
            .l()?;
        let cbor_array: JByteArray = JPrimitiveArray::from(cbor_body_inner);
        let native_cbor = env.convert_byte_array(cbor_array)?;
        builder.body_cbor(Some(native_cbor));
    }
    Ok(builder
        .body_msgpack(None)
        .build()
        .expect("Failed to build opaque decision model. Should not happen"))
}

fn decision_to_java_opaque<'a>(
    env: &mut JNIEnv<'a>,
    m: &dyn DecisionModel,
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let optional_class = env.find_class("java/util/Optional")?;
    let class = env.find_class("idesyde/core/OpaqueDecisionModel")?;
    let category = env.new_string(m.category())?;
    let part = env.new_object(set_class, "()V", &[])?;
    for s in &m.part() {
        let java_string = env.new_string(s)?;
        env.call_method(
            &part,
            "add",
            "(Ljava/lang/Object;)Z",
            &[JValue::Object(java_string.as_ref())],
        )?;
    }
    let body_cbor = env.byte_array_from_slice(
        m.body_as_cbor()
            .expect("Failed to get CBOR body of a decision model")
            .as_slice(),
    )?;
    let opt_body_cbor = env.call_static_method(
        optional_class.borrow(),
        "of",
        "(Ljava/lang/Object;)Ljava/util/Optional;",
        &[JValue::Object(body_cbor.as_ref())],
    )?;
    let body_json = env.new_string(
        m.body_as_json()
            .expect("Failed to get json body of a decision model"),
    )?;
    let opt_body_json = env.call_static_method(
        optional_class.borrow(),
        "of",
        "(Ljava/lang/Object;)Ljava/util/Optional;",
        &[JValue::Object(body_json.as_ref())],
    )?;
    let opt_empty =
        env.call_static_method(optional_class, "empty", "()Ljava/util/Optional;", &[])?;
    let obj = env.new_object(class, "(Ljava/lang/String;Ljava/util/Set;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V", &[
        JValue::Object(category.as_ref()),
        JValue::Object(part.as_ref()),
        opt_body_json.borrow(),
        opt_empty.borrow(),
        opt_body_cbor.borrow()
    ])?;
    Ok(obj)
}

fn decision_slide_to_java_set<'a>(
    env: &mut JNIEnv<'a>,
    decision_models: &[Arc<dyn idesyde_core::DecisionModel>],
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let decision_set = env.new_object(set_class, "()V", &[])?;
    for m in decision_models {
        let opaque = decision_to_java_opaque(env, m.as_ref())?;
        env.call_method(
            &decision_set,
            "add",
            "(Ljava/lang/Object;)Z",
            &[JValue::Object(opaque.as_ref())],
        )?;
    }
    Ok(decision_set)
}

fn design_slice_to_java_set<'a>(
    env: &mut JNIEnv<'a>,
    design_models: &[Arc<dyn idesyde_core::DesignModel>],
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let design_set = env.new_object(set_class, "()V", &[])?;
    for m in design_models {
        let opaque = design_model_to_java_opaque(env, m.as_ref())?;
        env.call_method(
            &design_set,
            "add",
            "(Ljava/lang/Object;)Z",
            &[JValue::Object(opaque.as_ref())],
        )?;
    }
    Ok(design_set)
}

fn java_design_set_to_rust<'a>(
    env: &mut JNIEnv<'a>,
    java_set: JObject<'a>,
) -> Result<HashSet<Arc<dyn DesignModel>>, jni::errors::Error> {
    let mut set: HashSet<Arc<dyn DesignModel>> = HashSet::new();
    let string_cls = env.find_class("java/lang/String")?;
    let initial_string = env.new_string("")?;
    let num_reversed_models = env.call_method(&java_set, "size", "()I", &[])?;
    let string_array = env.new_object_array(0, string_cls, &initial_string)?;
    let array_of_set = JObjectArray::from(
        env.call_method(
            &java_set,
            "toArray",
            "()[Ljava/lang/Object;",
            &[JValue::Object(string_array.as_ref())],
        )?
        .l()?,
    );
    for i in 0..num_reversed_models.i()? {
        let elem = env.get_object_array_element(&array_of_set, i)?;
        let rust_design = java_to_rust_design_model(env, &elem)?;
        set.insert(Arc::new(rust_design));
    }
    Ok(set)
}

fn java_to_rust_identification_result<'a>(
    env: &mut JNIEnv<'a>,
    java_result: JObject<'a>,
) -> IdentificationResult {
    // TODO: fix this conservative memory allocation here
    let max_local_references = 2* env.call_method(&java_result, "part", "()I", &[]).and_then(|x| x.i()).unwrap_or(0i32);
    env.with_local_frame(max_local_references, |env_inner| {
        let identified_array = env_inner
            .call_method(
                &java_result,
                "identifiedAsArray",
                "()[Lidesyde/core/DecisionModel;",
                &[],
            )
            .and_then(|x| x.l())
            .map(|x| JObjectArray::from(x))?;
        let identified_array_size = env_inner.get_array_length(identified_array.borrow())?; 
        let identified = (0..identified_array_size)
            .map(|i| {
                let elem = env_inner.get_object_array_element(&identified_array, i)?;
                java_to_rust_decision_model(env_inner, &elem)
            })
            .flatten()
            .map(|x| Arc::new(x) as Arc<dyn DecisionModel>)
            .collect();
        let messages_array = env_inner
            .call_method(
                &java_result,
                "messagesAsArray",
                "()[Ljava/lang/String;",
                &[],
            )
            .and_then(|x| x.l())
            .map(|x| JObjectArray::from(x))?;
        let identified_array_size = env_inner.get_array_length(messages_array.borrow())?;
        let messages = (0..identified_array_size)
            .map(|i| {
                let elem = env_inner.get_object_array_element(&messages_array, i)?;
                env_inner.get_string(&JString::from(elem))
                    .map(|x| x.to_str().map(|x| x.to_owned()))
                    .map(|x| x.unwrap())
            })
            .flatten()
            .collect();
        Ok::<IdentificationResult, jni::errors::Error>((identified, messages))
    }).unwrap_or((vec![], vec![]))
}

struct JavaModuleIdentificationRule {
    pub java_vm: Arc<JavaVM>,
    pub class_canonical_name: String,
}

impl IdentificationRuleLike for JavaModuleIdentificationRule {
    fn identify(
        &self,
        design_models: &[Arc<dyn idesyde_core::DesignModel>],
        decision_models: &[Arc<dyn idesyde_core::DecisionModel>],
    ) -> idesyde_core::IdentificationResult {
        let mut identified: Vec<Arc<dyn DecisionModel>> = vec![];
        let mut messages: Vec<String> = vec![];
        if let Ok(mut env_root) = self.java_vm.attach_current_thread() {
            let required_references = 2 + 9 + decision_models.iter().flat_map(DecisionModel::part).count() as i32 + 6 + design_models.iter().map(|x| x.elements().len()).sum::<usize>() as i32;
            let jresult = env_root.with_local_frame(required_references, |mut env| {
                    if let Ok(jirule) = env.find_class(&self.class_canonical_name.replace(".", "/")).and_then(|cls| env.new_object(cls, "()V", &[])) {
                        let jdesings_opt = design_slice_to_java_set(&mut env, design_models);
                        let jdecisions_opt = decision_slide_to_java_set(&mut env, decision_models);
                        match (jdesings_opt, jdecisions_opt) {
                            (Ok(jdesigns), Ok(jdecisions)) => {
                                match env.call_method(jirule, "apply", "(Ljava/util/Set;Ljava/util/Set;)Lidesyde/core/IdentificationResult;", &[JValue::Object(jdesigns.as_ref()), JValue::Object(jdecisions.as_ref())]) {
                                    Ok(irecord) => return irecord.l().map(|result| java_to_rust_identification_result(env, result)),
                                    Err(e) => {
                                        messages.push(format!("[<ERROR>]{}", e));
                                    }
                                }
                            }
                            _ => println!("Failed to convert Rust to Java and apply irule. Trying to proceed anyway.")
                        }
                    }
                    Err(jni::errors::Error::JavaException)
                });
            let (ms, msgs) = jresult.unwrap_or((vec![], vec![]));
            identified.extend(ms.into_iter());
            messages.extend(msgs.into_iter());
        }
        (identified, messages)
    }

    fn uses_design_models(&self) -> bool {
        true
    }

    fn uses_decision_models(&self) -> bool {
        true
    }

    fn uses_specific_decision_models(&self) -> Option<Vec<String>> {
        None
    }
}
struct JavaModuleReverseIdentificationRule {
    pub java_vm: Arc<JavaVM>,
    pub class_canonical_name: String,
}

impl ReverseIdentificationRuleLike for JavaModuleReverseIdentificationRule {
    fn reverse_identify(
        &self,
        decision_models: &[Arc<dyn DecisionModel>],
        design_models: &[Arc<dyn DesignModel>],
    ) -> idesyde_core::ReverseIdentificationResult {
        let mut reversed: Vec<Arc<dyn DesignModel>> = vec![];
        let mut messages: Vec<String> = vec![];
        match self.java_vm.attach_current_thread() {
            Ok(mut env) => match env.find_class(&self.class_canonical_name.replace(".", "/")) {
                Ok(cls) => match env.new_object(cls, "()V", &[]) {
                    Ok(obj) => match design_slice_to_java_set(&mut env, design_models) {
                        Ok(jdesigns) => {
                            match decision_slide_to_java_set(&mut env, decision_models) {
                                Ok(jdecisions) => {
                                    match env.call_method(
                                        obj,
                                        "apply",
                                        "(Ljava/util/Set;Ljava/util/Set;)Ljava/util/Set;",
                                        &[
                                            JValue::Object(jdesigns.as_ref()),
                                            JValue::Object(jdecisions.as_ref()),
                                        ],
                                    ) {
                                        Ok(irecord) => {
                                            if let Ok(java_result) = irecord.l() {
                                                if let Ok(java_reversed) =
                                                    java_design_set_to_rust(&mut env, java_result)
                                                {
                                                    reversed.extend(java_reversed.into_iter());
                                                }
                                            }
                                        }
                                        Err(e) => messages.push(format!("[<ERROR>]{}", e)),
                                    }
                                }
                                Err(e) => messages.push(format!("[<ERROR>]{}", e)),
                            }
                        }
                        Err(e) => messages.push(format!("[<ERROR>]{}", e)),
                    },
                    Err(e) => messages.push(format!("[<ERROR>]{}", e)),
                },
                Err(e) => messages.push(format!("[<ERROR>]{}", e)),
            },
            Err(e) => messages.push(format!("[<ERROR>]{}", e)),
        };
        (reversed, messages)
    }
}

fn instantiate_java_vm_debug(
    jar_files: &[std::path::PathBuf],
) -> Result<JavaVM, jni::errors::StartJvmError> {
    let mut builder = InitArgsBuilder::new()
        // Pass the JNI API version (default is 8)
        .version(JNIVersion::V8)
        .option("-Xcheck:jni");
    if !jar_files.is_empty() {
        let path_str = jar_files
            .iter()
            .map(|x| x.to_str().unwrap_or("."))
            .collect::<Vec<&str>>()
            .join(":");
        builder = builder.option(format!("-Djava.class.path={}", path_str));
    }
    JavaVM::new(
        builder
            .build()
            .expect("Init args should not fail to be built"),
    )
}

pub struct JavaModule {
    pub java_vm: Arc<JavaVM>,
    pub module_classes_canonical_name: String,
}

pub fn java_modules_from_jar_paths(paths: &[std::path::PathBuf]) -> Vec<JavaModule> {
    match instantiate_java_vm_debug(paths) {
        Ok(java_vm) => {
            let java_vm_arc = Arc::new(java_vm);
            let mut modules: Vec<JavaModule> = vec![];
            for path in paths {
                match std::fs::File::open(path) {
                    Ok(f) => match ZipArchive::new(f) {
                        Ok(mut jarfile) => match jarfile.by_name("META-INF/idesyde/automodules") {
                            Ok(mut automodules) => {
                                let mut contents = String::new();
                                if automodules.read_to_string(&mut contents).is_ok() {
                                    for line in contents.lines() {
                                        modules.push(JavaModule {
                                            java_vm: java_vm_arc.clone(),
                                            module_classes_canonical_name: line.to_string(),
                                        });
                                    }
                                };
                            }
                            Err(e) => println!("Error: {}", e),
                        },
                        Err(e) => println!("Error: {}", e),
                    },
                    Err(e) => println!("Error: {}", e),
                }
            }
            // for jarfile in paths.into_iter().map(|p| jars::jar(p, JarOptionBuilder::default())).flat_map(Result::ok) {
            //     println!("Jar opened");
            //     if let Some(automodules) = jarfile.files.get("META-INF/idesyde/automodules").map(|x| x.to_owned()).and_then(|x| String::from_utf8(x).ok()) {
            //         println!("Automodules found");
            //         for line in automodules.lines() {
            //             println!("Found module: {}", line);
            //             modules.push(JavaModule {
            //                 java_vm: java_vm_arc.clone(),
            //                 module_classes_canonical_name: line.to_string()
            //             });
            //         }
            //     }
            // }
            return modules;
        }
        Err(e) => println!("Error: {}", e),
    }
    vec![]
}

impl Module for JavaModule {
    fn unique_identifier(&self) -> String {
        self.module_classes_canonical_name.replace("AutoModule", "")
    }

    fn identification_rules(&self) -> Vec<Arc<dyn IdentificationRuleLike>> {
        let mut irules: Vec<Arc<dyn IdentificationRuleLike>> = vec![];
        if let Ok(mut env) = self.java_vm.attach_current_thread() {
            if let Ok(module_class) =
                env.find_class(&self.module_classes_canonical_name.replace('.', "/"))
            {
                if let Ok(module) = env.new_object(module_class, "()V", &[]) {
                    match env
                        .call_method(
                            module,
                            "identicationRulesCanonicalClassNames",
                            "()[Ljava/lang/String;",
                            &[],
                        )
                        .and_then(|x| x.l())
                    {
                        Ok(irules_classes_names) => {
                            let classes_names_array = JObjectArray::from(irules_classes_names);
                            let class_names_length = env
                                .get_array_length(classes_names_array.borrow())
                                .unwrap_or(0);
                            for i in 0..class_names_length {
                                if let Ok(irule) = env
                                    .get_object_array_element(&classes_names_array, i)
                                    .map(JString::from)
                                    .map(|x| JavaModuleIdentificationRule {
                                        java_vm: self.java_vm.clone(),
                                        class_canonical_name: env
                                            .get_string(&x)
                                            .map(|s| s.to_str().unwrap_or("").to_owned())
                                            .unwrap(),
                                    })
                                {
                                    irules.push(Arc::new(irule));
                                }
                            }
                        }
                        Err(e) => println!("Error: {}", e),
                    }
                }
            }
        }
        irules
    }

    fn explorers(&self) -> Vec<Arc<dyn idesyde_core::Explorer>> {
        Vec::new()
    }

    fn reverse_identification_rules(&self) -> Vec<Arc<dyn ReverseIdentificationRuleLike>> {
        let mut irules: Vec<Arc<dyn ReverseIdentificationRuleLike>> = vec![];
        if let Ok(mut env) = self.java_vm.attach_current_thread() {
            if let Ok(module_class) =
                env.find_class(&self.module_classes_canonical_name.replace('.', "/"))
            {
                if let Ok(module) = env.new_object(module_class, "()V", &[]) {
                    match env
                        .call_method(
                            module,
                            "identicationRulesCanonicalClassNames",
                            "()[Ljava/lang/String;",
                            &[],
                        )
                        .and_then(|x| x.l())
                    {
                        Ok(irules_classes_names) => {
                            let classes_names_array = JObjectArray::from(irules_classes_names);
                            let class_names_length = env
                                .get_array_length(classes_names_array.borrow())
                                .unwrap_or(0);
                            for i in 0..class_names_length {
                                if let Ok(irule) = env
                                    .get_object_array_element(&classes_names_array, i)
                                    .map(|x| JString::from(x))
                                    .map(|x| JavaModuleReverseIdentificationRule {
                                        java_vm: self.java_vm.clone(),
                                        class_canonical_name: env
                                            .get_string(&x)
                                            .map(|s| s.to_str().unwrap_or("").to_owned())
                                            .unwrap(),
                                    })
                                {
                                    irules.push(Arc::new(irule));
                                }
                            }
                        }
                        Err(e) => println!("Error: {}", e),
                    }
                }
            }
        }
        irules
    }

    fn identification_step(
        &self,
        _decision_models: &Vec<Arc<dyn DecisionModel>>,
        _design_models: &Vec<Arc<dyn DesignModel>>,
    ) -> IdentificationResult {
        (vec![], vec![])
    }

    fn reverse_identification(
        &self,
        _solved_decision_model: &Vec<Arc<dyn DecisionModel>>,
        _design_model: &Vec<Arc<dyn DesignModel>>,
    ) -> Vec<Arc<dyn DesignModel>> {
        vec![]
    }
}

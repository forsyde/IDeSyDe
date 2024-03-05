use std::{borrow::Borrow, collections::HashSet, sync::Arc};

use idesyde_core::{
    DecisionModel, DesignModel, IdentificationResult, IdentificationRuleLike, MarkedIdentificationRule, Module, OpaqueDecisionModel, OpaqueDecisionModelBuilder, OpaqueDesignModel, ReverseIdentificationRuleLike
};
use jni::{
    objects::{JByteArray, JObject, JObjectArray, JPrimitiveArray, JString, JValue}, AttachGuard, InitArgsBuilder, JNIVersion, JavaVM
};

struct JavaModuleIdentificationRule {
    pub java_vm: JavaVM,
    pub class_canonical_name: String,
}

struct JavaModuleReverseIdentificationRule {
    pub java_vm: JavaVM,
    pub class_canonical_name: String,
}

fn design_to_java_opaque<'a>(
    env: &mut AttachGuard<'a>,
    m: &dyn DesignModel,
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let class = env.find_class("idesyde/core/OpaqueDesignModel")?;
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
            "(Ljava/lang/Object;)B",
            &[JValue::Object(java_string.as_ref())],
        )?;
    }
    let obj = env.new_object(
        class,
        "(Ljava/util/String;Ljava/util/Set;Ljava/util/String;Ljava/util/String;)V",
        &[
            JValue::Object(category.as_ref()),
            JValue::Object(elems.as_ref()),
            JValue::Object(format.as_ref()),
            JValue::Object(body.as_ref()),
        ],
    )?;
    Ok(obj)
}

fn from_java_design_to_native<'a>(
    env: &mut AttachGuard<'a>,
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
    env.call_method(java_result, "elementsAsArray", "()[Ljava/util/String;", &[])?;
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


fn from_java_decision_to_native<'a>(
    env: &mut AttachGuard<'a>,
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
    env.call_method(java_result, "partAsArray", "()[Ljava/util/String;", &[])?;
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
        .call_method(java_result, "asCBORString", "()Ljava/util/Optional;", &[])?
        .l()?;
    let cbor_is_present = env.call_method(&cbor_body_obj, "isPresent", "()Z", &[])?;
    builder.body_cbor(None);
    if let Ok(true) = cbor_is_present.z() {
        let cbor_body_array = env.call_method(java_result, "asCBORBinary", "()[B", &[])?;
        let cbor_array: JByteArray = JPrimitiveArray::from(cbor_body_array.l()?);
        let native_cbor = env.convert_byte_array(cbor_array)?;
        builder.body_cbor(Some(native_cbor));
    }
    Ok(builder
        .body_msgpack(None)
        .build()
        .expect("Failed to build opaque decision model. Should not happen"))
}

fn decision_to_java_opaque<'a>(
    env: &mut AttachGuard<'a>,
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
            "(Ljava/lang/Object;)B",
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
        "(Ljava/lang/Object;)V",
        &[JValue::Object(body_cbor.as_ref())],
    )?;
    let body_json = env.new_string(
        m.body_as_json()
            .expect("Failed to get json body of a decision model"),
    )?;
    let opt_body_json = env.call_static_method(
        optional_class.borrow(),
        "of",
        "(Ljava/lang/Object;)V",
        &[JValue::Object(body_json.as_ref())],
    )?;
    let opt_empty = env.call_static_method(optional_class, "empty", "()V", &[])?;
    let obj = env.new_object(class, "(Ljava/util/String;Ljava/util/Set;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V", &[
        JValue::Object(category.as_ref()),
        JValue::Object(part.as_ref()),
        opt_body_json.borrow(),
        opt_empty.borrow(),
        opt_body_cbor.borrow()
    ])?;
    Ok(obj)
}

fn from_decision_slice_to_java_set<'a>(
    env: &mut AttachGuard<'a>,
    decision_models: &[Arc<dyn idesyde_core::DecisionModel>],
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let decision_set = env.new_object(set_class, "()V", &[])?;
    for m in decision_models {
        let opaque = decision_to_java_opaque(env, m.as_ref())?;
        env.call_method(
            &decision_set,
            "add",
            "(Ljava/lang/Object;)B",
            &[JValue::Object(opaque.as_ref())],
        )?;
    }
    Ok(decision_set)
}

fn from_design_slice_to_java_set<'a>(
    env: &mut AttachGuard<'a>,
    design_models: &[Arc<dyn idesyde_core::DesignModel>],
) -> Result<JObject<'a>, jni::errors::Error> {
    let set_class = env.find_class("java/util/HashSet")?;
    let design_set = env.new_object(set_class, "()V", &[])?;
    for m in design_models {
        let opaque = design_to_java_opaque(env, m.as_ref())?;
        env.call_method(
            &design_set,
            "add",
            "(Ljava/lang/Object;)B",
            &[JValue::Object(opaque.as_ref())],
        )?;
    }
    Ok(design_set)
}

fn from_java_string_set_to_rust<'a>(
    env: &mut AttachGuard<'a>,
    java_set: JObject<'a>,
) ->Result<HashSet<Arc<dyn DesignModel>>, jni::errors::Error> {
    let mut set: HashSet<Arc<dyn DesignModel>> = HashSet::new();
    let string_cls = env.find_class("java/lang/String")?;
    let initial_string = env.new_string("")?;
    let num_reversed_models = env.call_method(&java_set, "size", "()I", &[])?;
    let string_array = env.new_object_array(0, string_cls, &initial_string)?;
    let array_of_set = JObjectArray::from(env.call_method(&java_set, "toArray", "()[Ljava/lang/Object;", &[
        JValue::Object(string_array.as_ref())
    ])?.l()?);
    for i in 0..num_reversed_models.i()? {
        let elem = env.get_object_array_element(&array_of_set, i)?;
        let rust_design = from_java_design_to_native(env, &elem)?;
        set.insert(Arc::new(rust_design));
    }
    Ok(set)
}

fn java_result_to_result<'a>(
    env: &mut AttachGuard<'a>,
    java_result: JObject<'a>,
) -> IdentificationResult {
    let mut identified: Vec<Arc<dyn DecisionModel>> = vec![];
    if let Ok(identified_array) = env
        .call_method(
            &java_result,
            "identifiedAsArray",
            "()[Lidesyde/core/DecisionModel;",
            &[],
        )
        .and_then(|x| x.l())
        .map(|x| JObjectArray::from(x))
    {
        if let Ok(identified_array_size) = env.get_array_length(identified_array.borrow()) {
            identified = (0..identified_array_size)
                .map(|i| {
                    let elem = env.get_object_array_element(&identified_array, i).unwrap();
                    from_java_decision_to_native(env, &elem)
                })
                .flatten()
                .map(|x| Arc::new(x) as Arc<dyn DecisionModel>)
                .collect();
        }
    }
    let mut messages: Vec<String> = vec![];
    if let Ok(messages_array) = env
        .call_method(&java_result, "messagesAsArray", "()[java/util/String;", &[])
        .and_then(|x| x.l())
        .map(|x| JObjectArray::from(x))
    {
        if let Ok(identified_array_size) = env.get_array_length(messages_array.borrow()) {
            messages = (0..identified_array_size)
                .map(|i| {
                    let elem = env.get_object_array_element(&messages_array, i).unwrap();
                    env.get_string(&JString::from(elem))
                        .map(|x| x.to_str().map(|x| x.to_owned()))
                        .map(|x| x.unwrap())
                })
                .flatten()
                .collect();
        }
    }
    (identified, messages)
}

impl IdentificationRuleLike for JavaModuleIdentificationRule {
    fn identify(
        &self,
        design_models: &[Arc<dyn idesyde_core::DesignModel>],
        decision_models: &[Arc<dyn idesyde_core::DecisionModel>],
    ) -> idesyde_core::IdentificationResult {
        let mut identified: Vec<Arc<dyn DecisionModel>> = vec![];
        let mut messages: Vec<String> = vec![];
        match self.java_vm.attach_current_thread() {
            Ok(mut env) => match env.find_class(&self.class_canonical_name) {
                Ok(cls) => match env.new_object(cls, "()V", &[]) {
                    Ok(obj) => match from_design_slice_to_java_set(&mut env, design_models) {
                        Ok(jdesigns) => {
                            match from_decision_slice_to_java_set(&mut env, decision_models) {
                                Ok(jdecisions) => {
                                    match env.call_method(
                                        obj,
                                        "apply",
                                        "(Ljava/util/Set;Ljava/util/Set;)Lidesyde/core/IdentificationResult;",
                                        &[
                                            JValue::Object(jdesigns.as_ref()),
                                            JValue::Object(jdecisions.as_ref()),
                                        ],
                                    ) {
                                            Ok(irecord) => if let Ok(java_result) = irecord.l() {
                                                let (ms, msgs) = java_result_to_result(&mut env,java_result);   
                                                identified.extend(ms.into_iter());
                                                messages.extend(msgs.into_iter());
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

impl ReverseIdentificationRuleLike for JavaModuleReverseIdentificationRule {
    fn reverse_identify(
        &self,
        decision_models: &[Arc<dyn DecisionModel>],
        design_models: &[Arc<dyn DesignModel>],
    ) -> idesyde_core::ReverseIdentificationResult {
        let mut reversed: Vec<Arc<dyn DesignModel>> = vec![];
        let mut messages: Vec<String> = vec![];
        match self.java_vm.attach_current_thread() {
            Ok(mut env) => match env.find_class(&self.class_canonical_name) {
                Ok(cls) => match env.new_object(cls, "()V", &[]) {
                    Ok(obj) => match from_design_slice_to_java_set(&mut env, design_models) {
                        Ok(jdesigns) => {
                            match from_decision_slice_to_java_set(&mut env, decision_models) {
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
                                            Ok(irecord) => if let Ok(java_result) = irecord.l() {
                                                if let Ok(java_reversed) = from_java_string_set_to_rust(&mut env,java_result) {
                                                    reversed.extend(java_reversed.into_iter());
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

fn instantiate_java_vm_debug() -> Option<JavaVM> {
    InitArgsBuilder::new()
        // Pass the JNI API version (default is 8)
        .version(JNIVersion::V8)
        // You can additionally pass any JVM options (standard, like a system property,
        // or VM-specific).
        // Here we enable some extra JNI checks useful during development
        .option("-Xcheck:jni")
        .build().ok().and_then(|args| JavaVM::new(args).ok())
}

pub struct JavaModule {
    pub uid: String,
    pub java_vm: JavaVM,
    pub module_classes_canonical_name: String,
}

impl Module for JavaModule {
    fn unique_identifier(&self) -> String {
        self.uid.clone()
    }

    fn identification_rules(&self) -> Vec<Arc<dyn IdentificationRuleLike>> {
        if let Ok(mut env) = self.java_vm.attach_current_thread() {
            if let Ok(module_class) = env.find_class(&self.module_classes_canonical_name) {
                if let Ok(module) = env.new_object(module_class,"()V", &[]) {
                    if let Ok(irules_classes_names) = env.call_method(module, "identicationRulesCanonicalClassNames", "()[L/java/util/String;", &[]).and_then(|x| x.l()) {
                        let classes_names_array = JObjectArray::from(irules_classes_names);
                        let class_names_length = env.get_array_length(classes_names_array.borrow()).unwrap_or(0);
                        let mut irules: Vec<Arc<dyn IdentificationRuleLike>> = vec![];
                        for i in 0..class_names_length {
                            if let Ok(irule) = env.get_object_array_element(&classes_names_array, i).map(|x| JString::from(x)).map(|x| JavaModuleIdentificationRule {
                                java_vm: instantiate_java_vm_debug().unwrap(),
                                class_canonical_name: env.get_string(&x).map(|s| s.to_str().unwrap_or("").to_owned()).unwrap()
                            }) {
                                irules.push(Arc::new(irule));
                            }
                        }
                        return irules;
                    }

                }
            }
            // let irules_classes_names = env.call
        }
        vec![]
    }
}

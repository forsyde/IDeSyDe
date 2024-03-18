use std::{
    collections::{HashMap, HashSet},
    hash::Hash,
    io::Read,
    sync::{Arc, Mutex},
};

use idesyde_core::{
    DecisionModel, DesignModel, ExplorationBid, ExplorationConfiguration, ExplorationSolution,
    Explorer, IdentificationResult, IdentificationRuleLike, LoggedResult, Module,
    OpaqueDecisionModel, OpaqueDesignModel, ReverseIdentificationRuleLike,
};
use jni::{
    objects::{GlobalRef, JObject, JObjectArray, JPrimitiveArray, JString, JValue},
    InitArgsBuilder, JNIEnv, JNIVersion, JavaVM,
};
use zip::ZipArchive;

trait FromJava<'a, T>: Sized
where
    T: Into<JObject<'a>>,
{
    fn from_java(env: &mut JNIEnv<'a>, obj: T) -> Result<Self, jni::errors::Error>;
}

trait IntoJava<'a, T>
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error>;
}

impl<'a, T> IntoJava<'a, T> for f64
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        env.with_local_frame_returning_local(32, |inner| {
            let cls = inner.find_class("java/lang/Double")?;
            inner
                .call_static_method(
                    cls,
                    "valueOf",
                    "(D)Ljava/lang/Double;",
                    &[JValue::Double(*self)],
                )
                .and_then(|x| x.l())
        })
        .map(|x| T::from(x))
    }
}

impl<'a, T> IntoJava<'a, T> for u64
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        env.with_local_frame_returning_local(32, |inner| {
            let cls = inner.find_class("java/lang/Long")?;
            inner
                .call_static_method(
                    cls,
                    "valueOf",
                    "(J)Ljava/lang/Long;",
                    &[JValue::Long(*self as i64)],
                )
                .and_then(|x| x.l())
        })
        .map(|x| T::from(x))
    }
}

impl<'a, T> IntoJava<'a, T> for u32
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        env.with_local_frame_returning_local(32, |inner| {
            let cls = inner.find_class("java/lang/Integer")?;
            inner
                .call_static_method(
                    cls,
                    "valueOf",
                    "(I)Ljava/lang/Integer;",
                    &[JValue::Int(*self as i32)],
                )
                .and_then(|x| x.l())
        })
        .map(|x| T::from(x))
    }
}

impl<'a, T> IntoJava<'a, T> for bool
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        env.with_local_frame_returning_local(32, |inner| {
            let cls = inner.find_class("java/lang/Boolean")?;
            inner
                .call_static_method(
                    cls,
                    "valueOf",
                    "(Z)Ljava/lang/Boolean;",
                    &[JValue::Bool(*self as u8)],
                )
                .and_then(|x| x.l())
        })
        .map(|x| T::from(x))
    }
}

impl<'a, T> IntoJava<'a, T> for String
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        env.with_local_frame_returning_local(2 + 2 * self.len() as i32, |inner| {
            inner.new_string(self).map(|s| JObject::from(s))
        })
        .map(|x| T::from(x))
    }
}

impl<'a, T> IntoJava<'a, T> for &[u8]
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        env.with_local_frame_returning_local(2 + 2 * self.len() as i32, |inner| {
            inner.byte_array_from_slice(self).map(|x| JObject::from(x))
        })
        .map(|o| T::from(o))
    }
}

impl<'a, T> IntoJava<'a, T> for Vec<u8>
where
    T: From<JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<T, jni::errors::Error> {
        self.as_slice().into_java(env)
    }
}

impl<'a, T> IntoJava<'a, JObject<'a>> for Option<T>
where
    T: IntoJava<'a, JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let optional_class = env.find_class("java/util/Optional")?;
        match self {
            Some(x) => x.into_java(env).and_then(|javax| {
                env.with_local_frame_returning_local(32, |inner| {
                    inner
                        .call_static_method(
                            optional_class,
                            "of",
                            "(Ljava/lang/Object;)Ljava/util/Optional;",
                            &[JValue::Object(&javax.into())],
                        )
                        .and_then(|y| y.l())
                })
            }),
            None => env.with_local_frame_returning_local(32, |inner| {
                inner
                    .call_static_method(optional_class, "empty", "()Ljava/util/Optional;", &[])
                    .and_then(|x| x.l())
            }),
        }
    }
}

impl<'a, T> IntoJava<'a, JObject<'a>> for HashSet<T>
where
    T: IntoJava<'a, JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let set_class = env.find_class("java/util/HashSet")?;
        let set = env.with_local_frame_returning_local(16 + 2 * self.len() as i32, |inner| {
            inner.new_object(set_class, "()V", &[])
        })?;
        for elem in self {
            let elem = elem.into_java(env)?;
            env.call_method(
                &set,
                "add",
                "(Ljava/lang/Object;)Z",
                &[JValue::Object(elem.as_ref())],
            )?;
        }
        Ok(set)
    }
}

impl<'a, K, V> IntoJava<'a, JObject<'a>> for HashMap<K, V>
where
    K: IntoJava<'a, JObject<'a>>,
    V: IntoJava<'a, JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let map_cls = env.find_class("java/util/HashMap")?;
        let mapping = env
            .with_local_frame_returning_local(16 + 2 * self.len() as i32, |inner| {
                inner.new_object(map_cls, "()V", &[])
            })?;
        for (key, val) in self {
            let java_key = key.into_java(env)?;
            let elem = val.into_java(env)?;
            env.call_method(
                &mapping,
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                &[JValue::Object(&java_key), JValue::Object(&elem)],
            )?;
        }
        Ok(mapping)
    }
}

impl<'a, T> IntoJava<'a, JObjectArray<'a>> for &[T]
where
    T: IntoJava<'a, JObject<'a>>,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObjectArray<'a>, jni::errors::Error> {
        let cls = env.find_class("java/lang/Object")?;
        if let Some(fst) = self.first() {
            let fst_java = fst.into_java(env)?;
            let array = env
                .with_local_frame_returning_local(16 + 2 * self.len() as i32, |inner| {
                    inner
                        .new_object_array(self.len() as i32, &cls, fst_java)
                        .map(|o| JObject::from(o))
                })
                .map(|x| JObjectArray::from(x))?;
            for i in 1..self.len() {
                let x = &self[i];
                let elem = x.into_java(env)?;
                env.set_object_array_element(&array, i as i32, elem)?;
                // if let Some(elem) = self.get(i).and_then(|x| x.into_java(inner).ok()) {
                //     inner.set_object_array_element(&array, i as i32, elem)?;
                // }
            }
            Ok(array)
        } else {
            env.new_object_array(0, &cls, jni::objects::JObject::null())
                .map(|x| JObjectArray::from(x))
        }
    }
}

impl<'a> IntoJava<'a, JObject<'a>> for OpaqueDesignModel {
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let opaque_class = env.find_class("idesyde/core/OpaqueDesignModel")?;
        env.with_local_frame_returning_local(128 as i32, |inner| {
            let category: JString = self.category().into_java(inner)?;
            let format: JString = self.format().into_java(inner)?;
            let body = self
                .body_as_string()
                .and_then(|x| x.into_java(inner).ok())
                .unwrap_or(
                    inner
                        .new_string("")
                        .expect("Should not fail to create an empty string."),
                );
            let elems = self.elements().into_java(inner)?;
            inner.new_object(
                opaque_class,
                "(Ljava/lang/String;Ljava/util/Set;Ljava/lang/String;Ljava/lang/String;)V",
                &[
                    JValue::Object(category.as_ref()),
                    JValue::Object(elems.as_ref()),
                    JValue::Object(format.as_ref()),
                    JValue::Object(body.as_ref()),
                ],
            )
        })
    }
}

impl<'a> IntoJava<'a, JObject<'a>> for dyn DesignModel {
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        OpaqueDesignModel::from(self).into_java(env)
    }
}

impl<'a> IntoJava<'a, JObject<'a>> for OpaqueDecisionModel {
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let opaque_class = env.find_class("idesyde/core/OpaqueDecisionModel")?;
        env.with_local_frame_returning_local(self.part().len() as i32 + 128, |inner| {
            let category: JString = self.category().into_java(inner)?;
            let part = self.part().into_java(inner)?;
            let body_json = self.body_as_json().into_java(inner)?;
            let body_msgpack = self.body_as_msgpack().into_java(inner)?;
            let body_cbor = self.body_as_cbor().into_java(inner)?;
            inner.new_object(opaque_class, "(Ljava/lang/String;Ljava/util/Set;Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V", &[
                JValue::Object(category.as_ref()),
                JValue::Object(part.as_ref()),
                JValue::Object(body_json.as_ref()),
                JValue::Object(body_msgpack.as_ref()),
                JValue::Object(body_cbor.as_ref())
            ])
        })
    }
}

impl<'a> IntoJava<'a, JObject<'a>> for dyn DecisionModel {
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        OpaqueDecisionModel::from(self).into_java(env)
    }
}

impl<'a, T> IntoJava<'a, JObject<'a>> for Arc<T>
where
    T: IntoJava<'a, JObject<'a>> + ?Sized,
{
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        self.as_ref().into_java(env)
    }
}

impl<'a> IntoJava<'a, JObject<'a>> for ExplorationSolution {
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let sols = self.objectives.into_java(env)?;
        let decision = self.solved.into_java(env)?;
        env.with_local_frame_returning_local(128, |inner| {
            let cls = inner.find_class("idesyde/core/ExplorationSolution")?;
            inner.new_object(
                cls,
                "(Ljava/util/Map;Lidesyde/core/DecisionModel;)V",
                &[JValue::Object(&sols), JValue::Object(&decision)],
            )
        })
    }
}

impl<'a> IntoJava<'a, JObject<'a>> for ExplorationConfiguration {
    fn into_java(&self, env: &mut JNIEnv<'a>) -> Result<JObject<'a>, jni::errors::Error> {
        let cls = env.find_class("idesyde/core/Explorer$Configuration")?;
        // let max_sols: JObject = self.max_sols.into_java(env)?;
        // let total_timeout: JObject = self.total_timeout.into_java(env)?;
        // let improvement_timeout: JObject = self.improvement_timeout.into_java(env)?;
        // let time_resolution: JObject = self.time_resolution.into_java(env)?;
        // let memory_resolution: JObject = self.memory_resolution.into_java(env)?;
        // let improvement_iterations: JObject = self.improvement_iterations.into_java(env)?;
        // let strict: JObject = self.strict.into_java(env)?;
        let target_objectives = self.target_objectives.into_java(env)?;
        env.with_local_frame_returning_local(128, |inner| {
            let conf = inner.new_object(cls, "()V", &[])?;
            inner.set_field(
                &conf,
                "totalExplorationTimeOutInSecs",
                "J",
                JValue::Long(self.total_timeout as i64),
            )?;
            inner.set_field(
                &conf,
                "improvementTimeOutInSecs",
                "J",
                JValue::Long(self.improvement_timeout as i64),
            )?;
            inner.set_field(
                &conf,
                "maximumSolutions",
                "J",
                JValue::Long(self.max_sols as i64),
            )?;
            inner.set_field(
                &conf,
                "improvementIterations",
                "J",
                JValue::Long(self.improvement_iterations as i64),
            )?;
            inner.set_field(
                &conf,
                "timeDiscretizationFactor",
                "J",
                JValue::Long(self.time_resolution as i64),
            )?;
            inner.set_field(
                &conf,
                "memoryDiscretizationFactor",
                "J",
                JValue::Long(self.memory_resolution as i64),
            )?;
            inner.set_field(&conf, "strict", "Z", JValue::Bool(self.strict as u8))?;
            inner.set_field(
                &conf,
                "targetObjectives",
                "Ljava/util/Set;",
                JValue::Object(&target_objectives),
            )?;
            Ok(conf)
        })
    }
}

impl<'a> FromJava<'a, JObject<'a>> for f64 {
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<f64, jni::errors::Error> {
        env.with_local_frame(32, |inner| {
            inner
                .call_method(&obj, "doubleValue", "()D", &[])
                .and_then(|x| x.d())
        })
    }
}

impl<'a> FromJava<'a, JObject<'a>> for String {
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<String, jni::errors::Error> {
        env.with_local_frame_returning_local(256, |inner| {
            inner
                .call_method(&obj, "toString", "()Ljava/lang/String;", &[])
                .and_then(|x| x.l())
        })
        .map(|s| JString::from(s))
        .and_then(|s| {
            env.get_string(&s)
                .map(|ins| ins.to_str().map(|x| x.to_owned()).unwrap_or("".to_string()))
        })
    }
}

impl<'a> FromJava<'a, JString<'a>> for String {
    fn from_java(env: &mut JNIEnv<'a>, obj: JString<'a>) -> Result<String, jni::errors::Error> {
        env.get_string(&obj)
            .map(|x| x.to_str().map(|x| x.to_owned()).unwrap_or("".to_string()))
    }
}

impl<'a, T> FromJava<'a, JObject<'a>> for Option<T>
where
    T: Sized + FromJava<'a, JObject<'a>>,
{
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Option<T>, jni::errors::Error> {
        env.ensure_local_capacity(16 as i32)?;
        let is_present = env.call_method(&obj, "isPresent", "()Z", &[])?;
        if let Ok(true) = is_present.z() {
            let opt = env
                .call_method(&obj, "get", "()Ljava/lang/Object;", &[])?
                .l()?;
            let inside = T::from_java(env, opt)?;
            Ok(Some(inside))
        } else {
            Ok(None)
        }
    }
}

impl<'a, T> FromJava<'a, JObject<'a>> for Arc<T>
where
    T: FromJava<'a, JObject<'a>> + ?Sized,
{
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Arc<T>, jni::errors::Error> {
        T::from_java(env, obj).map(|x| Arc::new(x))
    }
}

impl<'a, T> FromJava<'a, JObject<'a>> for HashSet<T>
where
    T: Eq + PartialEq + Hash + FromJava<'a, JObject<'a>>,
{
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<HashSet<T>, jni::errors::Error> {
        let mut set: HashSet<T> = HashSet::new();
        let set_size = env.call_method(&obj, "size", "()I", &[])?.i()?;
        env.ensure_local_capacity(128 as i32 + 2 * set_size)?;
        let iter = env
            .call_method(&obj, "iterator", "()Ljava/util/Iterator;", &[])
            .and_then(|x| x.l())?;
        while env
            .call_method(&iter, "hasNext", "()Z", &[])
            .and_then(|x| x.z())?
            == true
        {
            let elem = env
                .call_method(&iter, "next", "()Ljava/lang/Object;", &[])?
                .l()?;
            let elem = T::from_java(env, elem)?;
            set.insert(elem);
        }
        Ok(set)
    }
}

impl<'a, K, V> FromJava<'a, JObject<'a>> for HashMap<K, V>
where
    K: Eq + PartialEq + Hash + FromJava<'a, JObject<'a>>,
    V: FromJava<'a, JObject<'a>>,
{
    fn from_java(
        env: &mut JNIEnv<'a>,
        obj: JObject<'a>,
    ) -> Result<HashMap<K, V>, jni::errors::Error> {
        let mut mapping: HashMap<K, V> = HashMap::new();
        let iter = env.with_local_frame_returning_local(32, |inner| {
            let entry_set = inner
                .call_method(&obj, "entrySet", "()Ljava/util/Set;", &[])
                .and_then(|x| x.l())?;
            inner
                .call_method(&entry_set, "iterator", "()Ljava/util/Iterator;", &[])
                .and_then(|x| x.l())
        })?;
        while env
            .call_method(&iter, "hasNext", "()Z", &[])
            .and_then(|x| x.z())?
            == true
        {
            let elem = env
                .call_method(&iter, "next", "()Ljava/lang/Object;", &[])?
                .l()?;
            let key_java = env
                .call_method(&elem, "getKey", "()Ljava/lang/Object;", &[])
                .and_then(|x| x.l())?;
            let key = K::from_java(env, key_java)?;
            let val_java = env
                .call_method(&elem, "getValue", "()Ljava/lang/Object;", &[])
                .and_then(|x| x.l())?;
            let val = V::from_java(env, val_java)?;
            mapping.insert(key, val);
        }
        Ok(mapping)
    }
}

impl<'a, T> FromJava<'a, JObject<'a>> for Vec<T>
where
    T: PartialEq + FromJava<'a, JObject<'a>>,
{
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Vec<T>, jni::errors::Error> {
        let mut vector: Vec<T> = vec![];
        let vec_size = env.call_method(&obj, "size", "()I", &[])?.i()?;
        env.ensure_local_capacity(128 as i32 + 2 * vec_size)?;
        let iter = env
            .call_method(&obj, "iterator", "()Ljava/util/Iterator;", &[])
            .and_then(|x| x.l())?;
        while env
            .call_method(&iter, "hasNext", "()Z", &[])
            .and_then(|x| x.z())?
            == true
        {
            let elem = env
                .call_method(&iter, "next", "()Ljava/lang/Object;", &[])?
                .l()?;
            let elem = T::from_java(env, elem)?;
            vector.push(elem);
        }
        Ok(vector)
    }
}

impl<'a> FromJava<'a, JObject<'a>> for Vec<u8> {
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Vec<u8>, jni::errors::Error> {
        let arr = JPrimitiveArray::from(obj);
        env.convert_byte_array(arr)
    }
}

impl<'a> FromJava<'a, JObject<'a>> for OpaqueDecisionModel {
    fn from_java(
        env: &mut JNIEnv<'a>,
        obj: JObject<'a>,
    ) -> Result<OpaqueDecisionModel, jni::errors::Error> {
        let mut builder = OpaqueDecisionModel::builder();
        env.with_local_frame(512, |inner| {
            let category_obj = inner
                .call_method(&obj, "category", "()Ljava/lang/String;", &[])?
                .l()?;
            builder.category(String::from_java(inner, JString::from(category_obj))?);
            let json_obj = inner
                .call_method(&obj, "asJsonString", "()Ljava/util/Optional;", &[])?
                .l()?;
            builder.body_json(Option::from_java(inner, json_obj)?);
            let cbor_obj = inner
                .call_method(&obj, "asCBORBinary", "()Ljava/util/Optional;", &[])?
                .l()?;
            builder.body_cbor(Option::from_java(inner, cbor_obj)?);
            let part = inner
                .call_method(&obj, "part", "()Ljava/util/Set;", &[])?
                .l()?;
            builder.part(HashSet::from_java(inner, part)?);
            Ok(builder
                .body_msgpack(None)
                .build()
                .expect("Failed to build opaque decision model. Should not happen"))
        })
    }
}

impl<'a> FromJava<'a, JObject<'a>> for OpaqueDesignModel {
    fn from_java(
        env: &mut JNIEnv<'a>,
        obj: JObject<'a>,
    ) -> Result<OpaqueDesignModel, jni::errors::Error> {
        let mut builder = OpaqueDesignModel::builder();
        env.with_local_frame(512, |inner| {
            let category_obj = inner
                .call_method(&obj, "category", "()Ljava/lang/String;", &[])?
                .l()?;
            builder.category(String::from_java(inner, JString::from(category_obj))?);
            let format_obj = inner
                .call_method(&obj, "format", "()Ljava/lang/String;", &[])?
                .l()?;
            builder.format(String::from_java(inner, JString::from(format_obj))?);
            let body_obj = inner
                .call_method(&obj, "asString", "()Ljava/util/Optional;", &[])?
                .l()?;
            builder.body(Option::from_java(inner, body_obj)?);
            let elems = inner
                .call_method(&obj, "elements", "()Ljava/util/Set;", &[])?
                .l()?;
            builder.elements(HashSet::from_java(inner, elems)?);
            Ok(builder
                .build()
                .expect("Failed to build opaque decision model. Should not happen"))
        })
    }
}

impl<'a> FromJava<'a, JObject<'a>> for IdentificationResult {
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self, jni::errors::Error> {
        // TODO: fix this conservative memory allocation here
        let decisions: HashSet<OpaqueDecisionModel> = env
            .call_method(&obj, "identified", "()Ljava/util/Set;", &[])
            .and_then(|x| x.l())
            .and_then(|x| HashSet::from_java(env, x))?;
        let dyn_decisions = decisions
            .into_iter()
            .map(|x| Arc::new(x) as Arc<dyn DecisionModel>)
            .collect();
        let messages: HashSet<String> = env
            .call_method(&obj, "messages", "()Ljava/util/Set;", &[])
            .and_then(|x| x.l())
            .and_then(|x| HashSet::from_java(env, x))?;
        Ok((dyn_decisions, messages.into_iter().collect()))
    }
}

// impl<'a> FromJava<'a, JObject<'a>> for dyn DecisionModel {
//     fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self, jni::errors::Error> {
//         OpaqueDecisionModel::from_java(env, obj).map(|x| &x as &dyn DecisionModel)
//     }
// }

// impl<'a> FromJava<'a, JObject<'a>> for dyn DesignModel {
//     fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self, jni::errors::Error> {
//         OpaqueDesignModel::from_java(env, obj).map(|x| &x as &dyn DesignModel)
//     }
// }

impl<'a> FromJava<'a, JObject<'a>> for ExplorationBid {
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self, jni::errors::Error> {
        let mut builder = ExplorationBid::builder();
        env.with_local_frame(512, |inner| {
            let objs_set: HashSet<String> = inner
                .call_method(&obj, "targetObjectives", "()Ljava/util/Set;", &[])
                .and_then(|x| x.l())
                .and_then(|x| HashSet::from_java(inner, x))?;
            builder.target_objectives(objs_set);
            let can_explore = inner
                .call_method(&obj, "canExplore", "()Ljava/lang/Boolean;", &[])
                .and_then(|x| x.l())
                .and_then(|x| inner.call_method(&x, "booleanValue", "()Z", &[]))
                .and_then(|x| x.z())
                .unwrap_or(false);
            builder.can_explore(can_explore);
            let competitiveness = inner
                .call_method(&obj, "competitiveness", "()Ljava/lang/Double;", &[])
                .and_then(|x| x.l())
                .and_then(|x| inner.call_method(&x, "doubleValue", "()D", &[]))
                .and_then(|x| x.d())
                .map(|f| f as f32)
                .unwrap_or(100.0f32);
            builder.competitiveness(competitiveness);
            let is_exact = inner
                .call_method(&obj, "isExact", "()Ljava/lang/Boolean;", &[])
                .and_then(|x| x.l())
                .and_then(|x| inner.call_method(&x, "booleanValue", "()Z", &[]))
                .and_then(|x| x.z())
                .unwrap_or(false);
            builder.is_exact(is_exact);
            Ok(builder
                .build()
                .expect("Should never fail to build a bidding."))
        })
    }
}

impl<'a> FromJava<'a, JObject<'a>> for ExplorationSolution {
    fn from_java(env: &mut JNIEnv<'a>, obj: JObject<'a>) -> Result<Self, jni::errors::Error> {
        let java_model: JObject = env
            .call_method(&obj, "solved", "()Lidesyde/core/DecisionModel;", &[])?
            .l()?;
        let java_opaque = env
            .call_static_method(
                "idesyde/core/OpaqueDecisionModel",
                "from",
                "(Lidesyde/core/DecisionModel;)Lidesyde/core/OpaqueDecisionModel;",
                &[JValue::Object(&java_model)],
            )?
            .l()?;
        let solved = Arc::new(OpaqueDecisionModel::from_java(env, java_opaque)?);
        let objectives: HashMap<String, f64> = env
            .call_method(&obj, "objectives", "()Ljava/util/Map;", &[])
            .and_then(|x| x.l())
            .and_then(|x| HashMap::from_java(env, x))?;
        Ok(ExplorationSolution {
            solved: solved,
            objectives: objectives,
        })
    }
}

#[derive(Clone)]
struct JavaModuleIdentificationRule {
    pub java_vm: Arc<JavaVM>,
    pub irule_jobject: GlobalRef,
}

impl IdentificationRuleLike for JavaModuleIdentificationRule {
    fn identify(
        &self,
        design_models: &[Arc<dyn idesyde_core::DesignModel>],
        decision_models: &[Arc<dyn idesyde_core::DecisionModel>],
    ) -> idesyde_core::IdentificationResult {
        let mut identified: Vec<Arc<dyn DecisionModel>> = vec![];
        let mut messages: Vec<String> = vec![];
        if let Ok(mut env_root) = self.java_vm.attach_current_thread_permanently() {
            let jresult = env_root.with_local_frame(128 + 2 * design_models.iter().map(|x| x.elements().len()).sum::<usize>() as i32, |env| {
                let jdesigns = design_models.into_java(env)?;
                let jdecisions = decision_models.into_java(env)?;
                match env.call_method(
                    &self.irule_jobject,
                    "fromArrays",
                    "([Lidesyde/core/DesignModel;[Lidesyde/core/DecisionModel;)Lidesyde/core/IdentificationResult;",
                    &[
                        JValue::Object(jdesigns.as_ref()),
                        JValue::Object(jdecisions.as_ref()),
                    ],
                ) {
                    Ok(irecord) => {
                        return irecord
                            .l()
                            .and_then(|result| IdentificationResult::from_java(env, result))
                    }
                    Err(e) => {
                        messages.push(format!("[<ERROR>]{}", e));
                    }
                }
                Err(jni::errors::Error::JavaException)
            });
            match jresult {
                Ok((ms, msgs)) => {
                    identified.extend(ms.into_iter());
                    messages.extend(msgs.into_iter());
                }
                Err(e) => {
                    messages.push(format!("[<ERROR>] {}", e));
                }
            }
        }
        (identified, messages)
    }

    fn uses_design_models(&self) -> bool {
        if let Ok(mut env_root) = self.java_vm.attach_current_thread_permanently() {
            return env_root
                .call_method(&self.irule_jobject, "usesDesignModels", "()Z", &[])
                .and_then(|x| x.z())
                .unwrap_or(true);
        }
        true
    }

    fn uses_decision_models(&self) -> bool {
        if let Ok(mut env_root) = self.java_vm.attach_current_thread_permanently() {
            return env_root
                .call_method(&self.irule_jobject, "usesDecisionModels", "()Z", &[])
                .and_then(|x| x.z())
                .unwrap_or(true);
        }
        true
    }

    fn uses_specific_decision_models(&self) -> Option<Vec<String>> {
        None
    }
}
struct JavaModuleReverseIdentificationRule {
    pub java_vm: Arc<JavaVM>,
    pub rrule_jobject: Arc<GlobalRef>,
}

impl ReverseIdentificationRuleLike for JavaModuleReverseIdentificationRule {
    fn reverse_identify(
        &self,
        decision_models: &[Arc<dyn DecisionModel>],
        design_models: &[Arc<dyn DesignModel>],
    ) -> idesyde_core::ReverseIdentificationResult {
        let mut reversed: Vec<OpaqueDesignModel> = vec![];
        let messages: Vec<String> = vec![];
        if let Ok(mut env_root) = self.java_vm.attach_current_thread_permanently() {
            let jresult =
                env_root.with_local_frame(128, |env| {
                    let jdesigns = design_models.into_java(env)?;
                    let jdecisions = decision_models.into_java(env)?;
                    let set_obj = env.call_method(
                    self.rrule_jobject.as_ref(),
                    "fromArrays",
                    "([Lidesyde/core/DecisionModel;[Lidesyde/core/DesignModel;)Ljava/util/Set;",
                    &[
                        JValue::Object(jdecisions.as_ref()),
                        JValue::Object(jdesigns.as_ref()),
                    ],
                )?.l()?;
                    HashSet::from_java(env, set_obj)
                });
            match jresult {
                Ok(reversed_set) => {
                    reversed.extend(reversed_set.into_iter());
                }
                Err(e) => println!("[<ERROR>] {}", e),
            }
        }
        (
            reversed
                .into_iter()
                .map(|x| Arc::new(x) as Arc<dyn DesignModel>)
                .collect(),
            messages,
        )
    }
}

fn instantiate_java_vm_debug(
    jar_files: &[std::path::PathBuf],
) -> Result<JavaVM, jni::errors::StartJvmError> {
    let mut builder = InitArgsBuilder::new()
        // Pass the JNI API version (default is 8)
        .version(JNIVersion::V8);
    if cfg!(debug_assertions) {
        builder = builder.option("-Xcheck:jni");
    }
    if !jar_files.is_empty() {
        let path_str = jar_files
            .iter()
            .map(|x| x.to_str().unwrap_or("."))
            .collect::<Vec<&str>>()
            .join(if cfg!(windows) { ";" } else { ":" });
        builder = builder.option(format!("-Djava.class.path={}", path_str));
    }
    JavaVM::new(
        builder
            .build()
            .expect("Init args should not fail to be built"),
    )
}

#[derive(Clone)]
pub struct JavaModuleExplorer {
    pub java_vm: Arc<JavaVM>,
    pub explorer_jobject: GlobalRef,
}

impl Explorer for JavaModuleExplorer {
    fn unique_identifier(&self) -> String {
        self.java_vm
            .attach_current_thread_permanently()
            .and_then(|mut env| {
                env.call_method(
                    &self.explorer_jobject,
                    "uniqueIdentifier",
                    "()Ljava/lang/String;",
                    &[],
                )
                .and_then(|x| x.l())
                .and_then(|x| {
                    env.get_string(&JString::from(x)).map(|s| {
                        s.to_str()
                            .expect("[<ERROR>] Failed converting name to UTF8")
                            .to_string()
                    })
                })
            })
            .expect("[<ERROR>] Could not load java module explorer's unique identifier.")
    }

    fn bid(&self, m: Arc<dyn DecisionModel>) -> idesyde_core::ExplorationBid {
        if let Ok(mut root_env) = self.java_vm.attach_current_thread_permanently() {
            let size_estimate = 3 * m.part().len() as i32;
            let java_bid_opt = root_env.with_local_frame_returning_local(size_estimate, |env| {
                match m.into_java(env) {
                    Ok(jmodel) => env
                        .call_method(
                            &self.explorer_jobject,
                            "bid",
                            "(Lidesyde/core/DecisionModel;)Lidesyde/core/ExplorationBidding;",
                            &[JValue::Object(jmodel.as_ref())],
                        )
                        .and_then(|x| x.l()),
                    Err(e) => {
                        println!(
                            "[<ERROR>] failed to convert {} to opaque with: {}",
                            m.category(),
                            e
                        );
                        Err(e)
                    }
                }
            });
            if let Ok(java_bid) = java_bid_opt {
                return ExplorationBid::from_java(&mut root_env, java_bid)
                    .unwrap_or(ExplorationBid::impossible());
            }
        }
        idesyde_core::ExplorationBid::impossible()
    }

    fn explore(
        &self,
        m: Arc<dyn DecisionModel>,
        currrent_solutions: &HashSet<idesyde_core::ExplorationSolution>,
        exploration_configuration: idesyde_core::ExplorationConfiguration,
    ) -> Arc<Mutex<dyn Iterator<Item = idesyde_core::ExplorationSolution> + Send + Sync>> {
        let java_vm = self.java_vm.clone();
        let exploration_iter = java_vm.attach_current_thread_permanently().and_then(|mut top_env| {
            let java_m = m.into_java(&mut top_env)?;
            let java_sols = currrent_solutions.into_java(&mut top_env)?;
            let java_conf = exploration_configuration.into_java(&mut top_env)?;
            let iter = top_env.with_local_frame_returning_local(1024, |env| {
                let stream = env.call_method(&self.explorer_jobject, "explore", "(Lidesyde/core/DecisionModel;Ljava/util/Set;Lidesyde/core/Explorer$Configuration;)Ljava/util/stream/Stream;", &[
                    JValue::Object(&java_m),
                    JValue::Object(&java_sols),
                    JValue::Object(&java_conf)
                ])?.l()?;
                env.call_method(&stream, "iterator", "()Ljava/util/Iterator;", &[])?.l()
            })?;
            top_env.new_global_ref(iter)
        });
        if let Ok(iter) = exploration_iter {
            Arc::new(Mutex::new(
                std::iter::repeat_with(move || {
                    if let Ok(mut top_env) = java_vm.attach_current_thread_permanently() {
                        let has_next = top_env
                            .call_method(&iter, "hasNext", "()Z", &[])
                            .and_then(|x| x.z());
                        if has_next.map(|x| x == true).unwrap_or(false) {
                            let next_java_opt =
                                top_env.with_local_frame_returning_local(1024, |env| {
                                    env.call_method(&iter, "next", "()Ljava/lang/Object;", &[])?
                                        .l()
                                });
                            if let Ok(next_java) = next_java_opt {
                                return ExplorationSolution::from_java(&mut top_env, next_java)
                                    .ok();
                            }
                        }
                    }
                    None
                })
                .take_while(|x| x.is_some())
                .flatten(),
            ))
        } else {
            Arc::new(Mutex::new(std::iter::empty()))
        }
    }
}

#[derive(Clone)]
pub struct JavaModule {
    pub java_vm: Arc<JavaVM>,
    pub module_jobject: GlobalRef,
    pub module_classes_canonical_name: String,
}

pub fn java_modules_from_jar_paths(paths: &[std::path::PathBuf]) -> LoggedResult<Vec<JavaModule>> {
    let mut modules = vec![];
    let mut warns = vec![];
    match instantiate_java_vm_debug(paths) {
        Ok(java_vm) => {
            let java_vm_arc = Arc::new(java_vm);
            for path in paths {
                match std::fs::File::open(path) {
                    Ok(f) => match ZipArchive::new(f) {
                        Ok(mut jarfile) => match jarfile.by_name("META-INF/idesyde/automodules") {
                            Ok(mut automodules) => {
                                let mut contents = String::new();
                                if automodules.read_to_string(&mut contents).is_ok() {
                                    for line in contents.lines() {
                                        let module_jobject = java_vm_arc
                                            .attach_current_thread_permanently()
                                            .and_then(|mut env| {
                                                env.ensure_local_capacity(100 as i32)?;
                                                env.find_class(line.replace('.', "/"))
                                                    .and_then(|module_class| {
                                                        env.new_object(module_class, "()V", &[])
                                                    })
                                                    .and_then(|module| env.new_global_ref(module))
                                            });
                                        if let Ok(global_ref) = module_jobject {
                                            modules.push(JavaModule {
                                                java_vm: java_vm_arc.clone(),
                                                module_classes_canonical_name: line.to_string(),
                                                module_jobject: global_ref,
                                            });
                                        }
                                    }
                                };
                            }
                            Err(_) => warns.push(format!(
                                "Could not open Manifest marker for JAR {}.",
                                path.display()
                            )),
                        },
                        Err(_) => {
                            warns.push(format!("Failed to open as a JAR {}.", path.display()))
                        }
                    },
                    Err(_) => warns.push(format!("Failed to open file {}.", path.display())),
                }
            }
        }
        Err(_) => warns.push("Failed to instantiate Java VM".to_string()),
    }
    LoggedResult::builder()
        .result(modules)
        .warn(warns)
        .build()
        .expect("LoggedResult should never fail to be built")
}

impl Module for JavaModule {
    fn unique_identifier(&self) -> String {
        self.java_vm
            .attach_current_thread_permanently()
            .and_then(|mut env| {
                env.call_method(
                    &self.module_jobject,
                    "uniqueIdentifier",
                    "()Ljava/lang/String;",
                    &[],
                )
                .and_then(|x| x.l())
                .and_then(|x| {
                    env.get_string(&JString::from(x)).map(|s| {
                        s.to_str()
                            .expect("[<ERROR>] Failed converting name to UTF8")
                            .to_string()
                    })
                })
            })
            .expect("[<ERROR>] Could not load java module explorer's unique identifier.")
    }

    fn identification_rules(&self) -> Vec<Arc<dyn IdentificationRuleLike>> {
        let mut irules: Vec<Arc<dyn IdentificationRuleLike>> = vec![];
        if let Ok(mut env) = self.java_vm.attach_current_thread_permanently() {
            match env
                .call_method(
                    &self.module_jobject,
                    "identificationRules",
                    "()Ljava/util/Set;",
                    &[],
                )
                .and_then(|x| x.l())
            {
                Ok(irules_objs) => {
                    let iter = env
                        .call_method(irules_objs, "iterator", "()Ljava/util/Iterator;", &[])
                        .and_then(|x| x.l())
                        .expect("Set to iterator should never fail");
                    while env
                        .call_method(&iter, "hasNext", "()Z", &[])
                        .and_then(|x| x.z())
                        .expect("Failed to get boolean from hasNext")
                        == true
                    {
                        let irule_obj = env
                            .call_method(&iter, "next", "()Ljava/lang/Object;", &[])
                            .expect("Failed to call next")
                            .l()
                            .expect("Failed to get object from next");
                        let irule = JavaModuleIdentificationRule {
                            java_vm: self.java_vm.clone(),
                            irule_jobject: env.new_global_ref(irule_obj).expect(
                                "Failed to make an irule a global variable. Should not happen.",
                            ),
                        };
                        irules.push(Arc::new(irule));
                    }
                }
                Err(e) => println!("Error: {}", e),
            }
        }
        irules
    }

    fn explorers(&self) -> Vec<Arc<dyn idesyde_core::Explorer>> {
        let mut explorers: Vec<Arc<dyn idesyde_core::Explorer>> = vec![];
        if let Ok(mut env) = self.java_vm.attach_current_thread_permanently() {
            match env
                .call_method(&self.module_jobject, "explorers", "()Ljava/util/Set;", &[])
                .and_then(|x| x.l())
            {
                Ok(explorers_objs) => {
                    let iter = env
                        .call_method(explorers_objs, "iterator", "()Ljava/util/Iterator;", &[])
                        .and_then(|x| x.l())
                        .expect("Set to iterator should never fail");
                    while env
                        .call_method(&iter, "hasNext", "()Z", &[])
                        .and_then(|x| x.z())
                        .expect("Failed to get boolean from hasNext")
                        == true
                    {
                        let explorer_obj = env
                            .call_method(&iter, "next", "()Ljava/lang/Object;", &[])
                            .expect("Failed to call next")
                            .l()
                            .expect("Failed to get object from next");
                        let explorer = JavaModuleExplorer {
                            java_vm: self.java_vm.clone(),
                            explorer_jobject: env.new_global_ref(explorer_obj).expect(
                                "Failed to make an irule a global variable. Should not happen.",
                            ),
                        };
                        explorers.push(Arc::new(explorer));
                    }
                }
                Err(e) => println!("Error: {}", e),
            }
        }
        explorers
    }

    fn reverse_identification_rules(&self) -> Vec<Arc<dyn ReverseIdentificationRuleLike>> {
        let mut rrules: Vec<Arc<dyn ReverseIdentificationRuleLike>> = vec![];
        if let Ok(mut env) = self.java_vm.attach_current_thread_permanently() {
            if let Ok(irules_set_obj) = env
                .call_method(
                    &self.module_jobject,
                    "reverseIdentificationRules",
                    "()Ljava/util/Set;",
                    &[],
                )
                .and_then(|x| x.l())
            {
                let iter = env
                    .call_method(&irules_set_obj, "iterator", "()Ljava/util/Iterator;", &[])
                    .and_then(|x| x.l())
                    .expect("Set to iterator should never fail");
                while env
                    .call_method(&iter, "hasNext", "()Z", &[])
                    .and_then(|x| x.z())
                    .expect("Failed to get boolean from hasNext")
                    == true
                {
                    let rrule_obj = env
                        .call_method(&iter, "next", "()Ljava/lang/Object;", &[])
                        .expect("Failed to call next")
                        .l()
                        .expect("Failed to get object from next");
                    let rrule = JavaModuleReverseIdentificationRule {
                        java_vm: self.java_vm.clone(),
                        rrule_jobject: Arc::new(env.new_global_ref(rrule_obj).expect(
                            "Failed to make an irule a global variable. Should not happen.",
                        )),
                    };
                    rrules.push(Arc::new(rrule));
                }
            }
        }
        rrules
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

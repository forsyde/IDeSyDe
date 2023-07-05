use proc_macro::TokenStream;
use quote::{format_ident, quote, ToTokens};
use syn::{DeriveInput, Field};

#[proc_macro_derive(DecisionModel, attributes(elems, relations))]
pub fn derive_decision_model(input: TokenStream) -> TokenStream {
    // Construct a string representation of the type definition
    let mut output = TokenStream::new();

    // Parse the string representation
    let ast: DeriveInput = syn::parse(input).unwrap();

    match ast.data {
        syn::Data::Struct(sdata) => {
            let mut elem_fields: Vec<&Field> = Vec::new();
            let mut rels_fields: Vec<&Field> = Vec::new();
            for f in &sdata.fields {
                if f.attrs.iter().any(|a| {
                    a.meta
                        .path()
                        .get_ident()
                        .map(|p| p.to_string() == "elems")
                        .unwrap_or(false)
                }) {
                    elem_fields.push(f);
                } else if f.attrs.iter().any(|a| {
                    a.meta
                        .path()
                        .get_ident()
                        .map(|p| p.to_string() == "relations")
                        .unwrap_or(false)
                }) {
                    rels_fields.push(f);
                }
            }
            let elem_names = elem_fields.iter().map(|f| f.ident.as_ref().unwrap());

            let uid = ast.ident.to_string();
            output.extend(TokenStream::from(quote! {
                impl idesyde_core::DecisionModel for #uid {
                    fn unique_identifier(&self) -> String {
                        "#uid".to_string()
                    }

                    fn header(&self) -> idesyde_core::headers::DecisionModelHeader {
                        let mut elems: Vec<String> = Vec::new();
                        let mut rels: Vec<idesyde_core::LabelledArcWithPorts> = Vec::new();
                        #(elems.extend(self.#elem_names.iter().map(|x| x.to_owned()));)"\n"*
                        DecisionModelHeader {
                            category: self.unique_identifier(),
                            body_path: None,
                            covered_elements: elems,
                            covered_relations: rels,
                        }
                    }
                }
            }));
        }
        _ => (),
    }
    output
    // Build the impl
    // let gen = impl_hello_world(&ast);

    // // Return the generated impl
    // gen.parse().unwrap()
}

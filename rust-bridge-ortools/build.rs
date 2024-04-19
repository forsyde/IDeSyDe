fn main() {
    let path = std::path::PathBuf::from("ortools/include"); // include path
    let mut b = autocxx_build::Builder::new("src/solutions.rs", &[&path])
        .extra_clang_args(&["-std=c++17"])
        .build()
        .expect("Failed to step build lib.rs with autocxx");
    // This assumes all your C++ bindings are in lib.rs
    b.flag_if_supported("-std=c++17")
        .compile("idesyde-ortools-solutions"); // arbitrary library name, pick anything
    println!("cargo:rerun-if-changed=src/solutions.rs");
    println!("cargo:rerun-if-changed=src/lib.rs");
    // Add instructions to link to any C++ libraries you need.
}

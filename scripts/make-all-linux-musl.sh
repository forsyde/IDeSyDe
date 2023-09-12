#!/bin/sh
sbt publishModules
./gradlew publishModules
rustup add target x86_64-unknown-linux-musl
cargo build --release --target x86_64-unknown-linux-musl
cp ./target/x86_64-unknown-linux-musl/release/idesyde-orchestration idesyde
zip -r idesyde-x86_64-unknown-linux-musl.zip idesyde imodules emodules
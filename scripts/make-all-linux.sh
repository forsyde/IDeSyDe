#!/bin/sh
sbt publishModules
./gradlew publishModules
cargo build --release --target x86_64-unknown-linux-musl
cp ./target/x86_64-unknown-linux-musl/release/idesyde-orchestrator idesyde
zip -r idesyde-linux-musl-x86_64.zip idesyde imodules emodules
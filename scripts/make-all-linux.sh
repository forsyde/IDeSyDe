#!/bin/sh
sbt publishModules
./gradlew publishModules
cargo build --release
cp ./target/release/idesyde-orchestration idesyde
zip -r idesyde-x86_64-unknown-linux-gnu.zip idesyde imodules emodules
#!/bin/sh
sbt publishModules
./gradlew publishModules
cargo build --release
cp ./target/release/idesyde-orchestration idesyde
zip -r idesyde-linux-gnu-x86_64.zip idesyde imodules emodules
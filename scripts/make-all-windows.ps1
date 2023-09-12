sbt publishModules
.\gradlew.bat publishModules
cargo build --release
cp .\target\release\idesyde-orchestration.exe idesyde.exe
zip -r idesyde-x86_64-pc-windows-msvc.zip idesyde.exe imodules emodules
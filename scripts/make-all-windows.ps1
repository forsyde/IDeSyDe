sbt publishModules
.\gradlew.bat publishModules
cargo build --release
cp .\target\release\idesyde-orchestration.exe idesyde.exe
zip -r idesyde-windows-msvc-x86_64.zip idesyde.exe imodules emodules
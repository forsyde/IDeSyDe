sbt publishModules
.\gradlew.bat publishModules
cargo build --release
Copy-Item .\target\release\idesyde-orchestration.exe idesyde.exe
zip -r idesyde-x86_64-pc-windows-msvc.zip idesyde.exe imodules emodules
sbt publishModules
.\gradlew.bat publishModules
cargo build --release
Copy-Item .\target\release\idesyde-orchestration.exe idesyde.exe
Compress-Archive -Force -Path idesyde.exe, modules -DestinationPath idesyde-x86_64-pc-windows-msvc.zip 
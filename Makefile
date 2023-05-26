# Taken from https://stackoverflow.com/questions/714100/os-detecting-makefile
ifeq ($(OS),Windows_NT)     # is Windows_NT on XP, 2000, 7, Vista, 10...
	ORCHESTRATOR_EXECUTABLE := idesyde\.exe
	ORCHESTRATOR_BUILT_PATH := .\target\release\idesyde-orchestration.exe
	TARGET_FLAG := ""
	FINAL_TRIPLET := idesyde-windows-$(shell echo $$Env:PROCESSOR_ARCHITECTURE.ToLower())
else
	ORCHESTRATOR_EXECUTABLE := idesyde
	ORCHESTRATOR_BUILT_PATH := ./target/x86_64-unknown-linux-musl/release/idesyde-orchestration idesyde
	TARGET_FLAG := "--target x86_64-unknown-linux-musl"
	FINAL_TRIPLET := idesyde-linux-$(shell uname -p)
endif

$(ORCHESTRATOR_EXECUTABLE):
	cargo build -r $(TARGET_FLAG)
	cp $(ORCHESTRATOR_BUILT_PATH) $(ORCHESTRATOR_EXECUTABLE)

imodules/idesyde-scala-bridge-devicetree.jar:
	sbt publishModules

imodules/idesyde-scala-bridge-forsyde-io.jar:
	sbt publishModules

imodules/idesyde-scala-bridge-matlab.jar:
	sbt publishModules

imodules/idesyde-scala-common.jar:
	sbt publishModules

emodules/idesyde-scala-choco.jar:
	sbt publishModules

$(FINAL_TRIPLET).zip: $(ORCHESTRATOR_EXECUTABLE) \
	imodules/idesyde-scala-bridge-devicetree.jar \ 
	imodules/idesyde-scala-bridge-forsyde-io.jar \ 
	imodules/idesyde-scala-bridge-matlab.jar \ 
	imodules/idesyde-scala-common.jar \ 
	emodules/idesyde-scala-choco.jar
ifeq ($(OS),Windows_NT)     # is Windows_NT on XP, 2000, 7, Vista, 10...
	Compress-Archive $(ORCHESTRATOR_EXECUTABLE),emodules,imodules $(FINAL_TRIPLET).zip
else
	zip -r $(FINAL_TRIPLET).zip $(ORCHESTRATOR_EXECUTABLE) imodules emodules
endif

all: $(FINAL_TRIPLET)\.zip
	echo $(FINAL_TRIPLET)\.zip

clean:
	$(RM) $(FINAL_TRIPLET).zip

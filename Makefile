# Taken from https://stackoverflow.com/questions/714100/os-detecting-makefile
ifeq ($(OS),Windows_NT)     # is Windows_NT on XP, 2000, 7, Vista, 10...
	ORCHESTRATOR_FINAL_EXECUTABLE := idesyde.exe
	ORCHESTRATOR_EXECUTABLE := idesyde-orchestration.exe
	BUILD_PATH := ./target/release/
	TARGET_FLAG := 
	FINAL_TRIPLET := idesyde-windows-$(shell echo $$Env:PROCESSOR_ARCHITECTURE.ToLower())
	CP := Copy-Item
else
	ORCHESTRATOR_FINAL_EXECUTABLE := idesyde
	ORCHESTRATOR_EXECUTABLE := idesyde-orchestration
	BUILD_PATH := ./target/x86_64-unknown-linux-musl/release/
	TARGET_FLAG := --target x86_64-unknown-linux-musl
	FINAL_TRIPLET := idesyde-linux-$(shell uname -p)
	CP := cp
endif

$(ORCHESTRATOR_FINAL_EXECUTABLE):
	cargo build --package idesyde-orchestration -r $(TARGET_FLAG)
	$(CP) $(BUILD_PATH)$(ORCHESTRATOR_EXECUTABLE) $(ORCHESTRATOR_FINAL_EXECUTABLE)

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

imodules/idesyde-rust-common.exe:
	cargo build --package idesyde-common
	cp $(BUILD_PATH)idesyde-common.exe ./imodules/idesyde-rust-common.exe

# $(FINAL_TRIPLET).zip: 
# ifeq ($(OS),Windows_NT)     # is Windows_NT on XP, 2000, 7, Vista, 10...
# 	Compress-Archive $(ORCHESTRATOR_FINAL_EXECUTABLE),emodules,imodules $(FINAL_TRIPLET).zip
# else
# 	zip -r $(FINAL_TRIPLET).zip $(ORCHESTRATOR_FINAL_EXECUTABLE) imodules emodules
# endif

all: $(ORCHESTRATOR_FINAL_EXECUTABLE) \
	imodules/idesyde-scala-bridge-devicetree.jar \ 
	imodules/idesyde-scala-bridge-forsyde-io.jar \ 
	imodules/idesyde-scala-bridge-matlab.jar \ 
	imodules/idesyde-scala-common.jar \
	imodules/idesyde-rust-common.jar
	emodules/idesyde-scala-choco.jar

zip: all

clean:
	$(RM) $(FINAL_TRIPLET).zip

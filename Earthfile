VERSION 0.7


build-scala-all:
    ARG jabba_jdk='amazon-corretto@1.17.0-0.35.1'
    ARG targets="x86_64-pc-windows-gnu x86_64-unknown-linux-musl"
    FROM debian:latest
    WORKDIR scala-workdir
    RUN apt-get update
    RUN apt-get install -y curl bash wget
    RUN curl -sL https://github.com/Jabba-Team/jabba/raw/main/install.sh | JABBA_COMMAND="install ${jabba_jdk} -o /jdk" bash
    ENV JAVA_HOME /jdk
    ENV PATH $JAVA_HOME/bin:$PATH
    COPY build.sbt build.sbt
    COPY project/plugins.sbt project/plugins.sbt
    COPY project/build.properties project/build.properties
    COPY --dir scala-core .
    COPY --dir scala-blueprints .
    COPY --dir scala-common .
    COPY --dir scala-bridge-device-tree .
    COPY --dir scala-bridge-forsyde-io .
    COPY --dir scala-bridge-matlab .
    COPY --dir scala-choco .
    COPY --dir scala-minizinc .
    RUN curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux-static.gz" | gzip -d > cs
    RUN chmod +x ./cs
    RUN eval "$(./cs setup --env --apps coursier,cs,sbt,sbtn,scala,scalac)" && ./cs launch sbt -- publishModules
    FOR target IN ${targets}
        FOR imodule IN $(cd imodules && ls *.jar)
            IF $(echo "${imodule}" | grep -q "idesyde-scala")
            ELSE
                RUN mv imodules/${imodule} imodules/idesyde-scala-${imodule}
            END
        END
        FOR emodule IN $(cd emodules && ls *.jar)
            IF $(echo "${emodule}" | grep -q "idesyde-scala")
            ELSE
                RUN mv emodules/${emodule} emodules/idesyde-scala-${emodule}
            END
        END
        SAVE ARTIFACT imodules/* ${target}/imodules/
        SAVE ARTIFACT emodules/* ${target}/emodules/
        SAVE ARTIFACT imodules/* AS LOCAL dist/${target}/imodules/
        SAVE ARTIFACT emodules/* AS LOCAL dist/${target}/emodules/
    END
    
build-rust-all:
    FROM debian:latest
    ENV RUSTUP_HOME=/rustup
    ENV CARGO_HOME=/cargo
    WORKDIR /rust-workdir
    RUN apk --no-cache add --update curl bash build-base openssl-dev pkgconfig
    RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y 
    COPY Cargo.toml .
    COPY --dir rust-core .
    COPY --dir rust-common .
    COPY --dir rust-orchestration .
    COPY --dir rust-blueprints .
    COPY --dir rust-bridge-matlab-simulink .

build-rust-linux-host:
    FROM +build-rust-all
    ARG targets="x86_64-unknown-linux-musl"
    FOR target IN ${targets}
        IF $(echo "${target}" | grep -q "windows")
            RUN apk --no-cache add --update mingw-w64-gcc mingw-w64-crt
        END
        RUN source "/cargo/env" && rustup target add ${target}
        RUN source "/cargo/env" && cargo build -r --target ${target}
        IF $(echo "${target}" | grep -q "windows")
            # Took away common module as it is embedded in the orchestrator now
            # SAVE ARTIFACT target/${target}/release/idesyde-common.exe ${target}/imodules/idesyde-rust-common.exe
            # SAVE ARTIFACT target/${target}/release/idesyde-common.exe AS LOCAL dist/${target}/imodules/idesyde-rust-common.exe
            SAVE ARTIFACT target/${target}/release/idesyde-orchestration.exe ${target}/idesyde.exe
            SAVE ARTIFACT target/${target}/release/idesyde-orchestration.exe AS LOCAL dist/${target}/idesyde.exe
        ELSE
            # Took away common module as it is embedded in the orchestrator now
            # SAVE ARTIFACT target/${target}/release/idesyde-common ${target}/imodules/idesyde-rust-common 
            # SAVE ARTIFACT target/${target}/release/idesyde-common AS LOCAL dist/${target}/imodules/idesyde-rust-common 
            SAVE ARTIFACT target/${target}/release/idesyde-orchestration ${target}/idesyde
            SAVE ARTIFACT target/${target}/release/idesyde-orchestration AS LOCAL dist/${target}/idesyde
        END
    END

build-rust-windows-native:
    FROM mcr.microsoft.com/windows/server:ltsc2022
    ENV RUSTUP_HOME=/rustup
    ENV CARGO_HOME=/cargo
    WORKDIR /rust-workdir
    RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain stable -y 
    COPY Cargo.toml .
    COPY --dir rust-core .
    COPY --dir rust-common .
    COPY --dir rust-orchestration .
    COPY --dir rust-blueprints .
    COPY --dir rust-bridge-matlab-simulink .
    RUN source "/cargo/env" && rustup target add x86_64-pc-windows-gnu
    RUN source "/cargo/env" && cargo build -r --target x86_64-pc-windows-gnu
    # Took away common module as it is embedded in the orchestrator now
    # SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-common.exe x86_64-windows-gnu/imodules/idesyde-rust-common.exe
    # SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-common.exe AS LOCAL dist/x86_64-windows-gnu/imodules/idesyde-rust-common.exe
    SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-orchestration.exe x86_64-windows-gnu/idesyde-orchestrator.exe
    SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-orchestration.exe AS LOCAL dist/x86_64-windows-gnu/idesyde-orchestrator.exe

zip-build:
    FROM alpine:latest
    ARG targets="x86_64-unknown-linux-musl"
    ARG tag="no-tag"
    WORKDIR /zipdir
    RUN apk --no-cache add --update zip
    FOR target IN ${targets}
        COPY --dir +build-scala-all/${target}/* ${target}/
        COPY --dir +build-rust-linux-host/${target}/* ${target}/
        RUN cd ${target} && zip -r idesyde-${tag}-${target}.zip *
        SAVE ARTIFACT ${target}/idesyde-${tag}-${target}.zip AS LOCAL dist/idesyde-${tag}-${target}.zip
        RUN rm -r ${target}
    END

dist-linux:
    ARG tag="no-tag"
    ARG jabba_jdk='amazon-corretto@1.17.0-0.35.1'
    BUILD +build-scala-all --targets="x86_64-unknown-linux-musl" --jabba_jdk=${jabba_jdk}
    BUILD +build-rust-linux-host --targets="x86_64-unknown-linux-musl"
    BUILD +zip-build --targets="x86_64-unknown-linux-musl" --tag=${tag}

dist-windows-cross:
    ARG tag="no-tag"
    ARG jabba_jdk='amazon-corretto@1.17.0-0.35.1'
    BUILD +build-scala-all --targets="x86_64-pc-windows-gnu" --jabba_jdk=${jabba_jdk}
    BUILD +build-rust-linux-host --targets="x86_64-pc-windows-gnu"
    BUILD +zip-build --targets="x86_64-pc-windows-gnu" --tag=${tag}

dist-all:
    ARG tag="no-tag"
    ARG jabba_jdk='amazon-corretto@1.17.0-0.35.1'
    BUILD +build-scala-all --targets="x86_64-unknown-linux-musl x86_64-pc-windows-gnu" --jabba_jdk=${jabba_jdk}
    BUILD +build-rust-linux-host --targets="x86_64-unknown-linux-musl x86_64-pc-windows-gnu"
    BUILD +zip-build --targets="x86_64-unknown-linux-musl x86_64-pc-windows-gnu" --tag=${tag}

test-case-studies:
    ARG test_slow="no"
    ARG jabba_jdk='amazon-corretto@1.17.0-0.35.1'
    ARG targets="x86_64-unknown-linux-musl"
    BUILD +build-scala-all --targets=${targets} --jdk_base=${jdk_base}
    BUILD +build-rust-linux-host --targets=${targets}
    FROM debian:latest
    RUN apt-get update
    RUN apt-get install -y curl bash wget python3 python3-pip python3-venv pipx
    RUN curl -sL https://github.com/Jabba-Team/jabba/raw/main/install.sh | JABBA_COMMAND="install ${jabba_jdk} -o /jdk" bash
    ENV JAVA_HOME /jdk
    ENV PATH $JAVA_HOME/bin:$PATH
    ENV TEST_SLOW=${test_slow}
    # RUN /robotenv/bin/python -m pip install robotframework
    FOR target IN ${targets}
        WORKDIR /${target}
        COPY --dir +build-scala-all/${target}/* .
        COPY --dir +build-rust-linux-host/${target}/* .
        COPY --dir examples_and_benchmarks .
        COPY *.py .
        COPY *.robot .
        RUN pipx run --spec robotframework robot --exclude slow TestsBenchmark.robot
        SAVE ARTIFACT report.html AS LOCAL report.html
        SAVE ARTIFACT log.html AS LOCAL log.html
        SAVE ARTIFACT output.xml AS LOCAL output.xml
    END
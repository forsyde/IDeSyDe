VERSION 0.7


build-scala:
    ARG jdk='17'
    FROM amazoncorretto:$jdk-alpine-jdk
    WORKDIR /scala-workdir
    RUN apk --no-cache add --update curl bash
    RUN curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux-static.gz" | gzip -d > cs
    RUN chmod +x ./cs
    RUN eval "$(./cs setup --env --apps coursier,cs,sbt,sbtn,scala,scalac --install-dir /coursier)"
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
    RUN /coursier/sbt publishModules
    FOR os IN x86_64-windows-gnu x86_64-unknown-linux
        FOR imodule IN $(cd imodules && ls imodules/*.jar)
            SAVE ARTIFACT imodules/${imodule} imodules/${os}/idesyde-scala-${imodule}
            RUN mv imodules/${imodule} imodules/idesyde-scala-${imodule}
        END
        FOR emodule IN $(cd emodules && ls emodules/*.jar)
            SAVE ARTIFACT emodules/${emodule} emodules/${os}/idesyde-scala-${emodule}
            RUN mv emodules/${emodule} emodules/idesyde-scala-${emodule}
        END
        SAVE ARTIFACT imodules/* AS LOCAL dist/${os}/imodules
        SAVE ARTIFACT emodules/* AS LOCAL dist/${os}/emodules
    END
    
build-rust:
    FROM alpine:latest
    ENV RUSTUP_HOME=/rustup
    ENV CARGO_HOME=/cargo
    WORKDIR /rust-workdir
    RUN apk --no-cache add --update curl bash build-base
    RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain none -y 
    COPY Cargo.toml .
    COPY --dir rust-core .
    COPY --dir rust-common .
    COPY --dir rust-orchestration .
    COPY --dir rust-blueprints .
    COPY --dir rust-bridge-matlab-simulink .

build-rust-linux:
    FROM +build-rust
    RUN source "/cargo/env" && rustup target add stable-x86_64-unknown-linux-musl
    RUN source "/cargo/env" && cargo build -r --target x86_64-unknown-linux-musl
    SAVE ARTIFACT target/x86_64-unknown-linux-musl/release/idesyde-common x86_64-unknown-linux/imodules/idesyde-rust-common 
    SAVE ARTIFACT target/x86_64-unknown-linux-musl/release/idesyde-common AS LOCAL dist/x86_64-unknown-linux/imodules/idesyde-rust-common 
    SAVE ARTIFACT target/x86_64-unknown-linux-musl/release/idesyde-orchestration x86_64-unknown-linux/idesyde-orchestrator
    SAVE ARTIFACT target/x86_64-unknown-linux-musl/release/idesyde-orchestration AS LOCAL dist/x86_64-unknown-linux/idesyde-orchestrator

build-rust-windows:
    FROM +build-rust
    RUN source "/cargo/env" && rustup target add stable-x86_64-pc-windows-gnu
    RUN source "/cargo/env" && cargo build -r --target x86_64-pc-windows-gnu
    SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-common x86_64-windows-gnu/imodules/idesyde-rust-common 
    SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-common AS LOCAL dist/x86_64-windows-gnu/imodules/idesyde-rust-common 
    SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-orchestration x86_64-windows-gnu/idesyde-orchestrator
    SAVE ARTIFACT target/x86_64-pc-windows-gnu/release/idesyde-orchestration AS LOCAL dist/x86_64-windows-gnu/idesyde-orchestrator

dist-linux:
    BUILD +build-scala
    BUILD +build-rust-linux

dist-windows:
    BUILD +build-scala
    BUILD +build-rust-windows

dist-all:
    BUILD +dist-linux
    BUILD +dist-windows
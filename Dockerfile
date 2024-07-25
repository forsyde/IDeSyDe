# syntax=docker/dockerfile:labs
FROM debian:bookworm-slim
# Set environment variables
ARG GRADLE_HOME=/opt/gradle
ARG GRADLE_VERSION=8.1-rc-1
ARG MINIZINC_VERSION=2.8.5
ARG OPENJDK_VERSION=17

# Install Java and other necessary packages
RUN apt-get update && \
    apt-get install -y build-essential wget curl apt-transport-https gnupg unzip zip git openjdk-${OPENJDK_VERSION}-jdk && \
    apt-get clean
# For Java
# ENV PATH="/usr/bin:${PATH}" 

# Install Cargo, build tool for the Rust parts of IDeSyDe
#! set version!!!
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH="/root/.cargo/bin:$PATH"


# Install Gradle, build tool the Java parts of IDeSyDe
# Gradle is also used for this repo's project  
RUN wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -o /tmp/gradle-${GRADLE_VERSION}-rc-1-bin.zip && \
    mkdir -p ${GRADLE_HOME} && \
    unzip -d ${GRADLE_HOME} gradle-${GRADLE_VERSION}-bin.zip && \
    rm gradle-${GRADLE_VERSION}-bin.zip
ENV PATH="${GRADLE_HOME}/gradle-${GRADLE_VERSION}/bin:${PATH}"

# Install SBT, build tool for the Scala parts of IDeSyDe
#! set version on L31
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list  && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import  && \
    chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg  && \
    apt-get update  && \
    apt-get install sbt 


# Install MiniZinc
RUN wget -c https://github.com/MiniZinc/MiniZincIDE/releases/download/${MINIZINC_VERSION}/MiniZincIDE-${MINIZINC_VERSION}-bundle-linux-x86_64.tgz && \
    tar -xf MiniZincIDE-${MINIZINC_VERSION}-bundle-linux-x86_64.tgz && \
    rm MiniZincIDE-${MINIZINC_VERSION}-bundle-linux-x86_64.tgz
ENV PATH="${PATH}:/MiniZincIDE-${MINIZINC_VERSION}-bundle-linux-x86_64/bin"

# Set the working directory
ENV ROOT_DIR=/app
ADD https://github.com/forsyde/IDeSyDe.git#develop /IDeSyDe

WORKDIR /IDeSyDe

RUN cargo build --release
RUN cp ./target/release/idesyde-orchestration idesyde
RUN sbt publishModules
RUN gradle publishModules

RUN apt-get purge -y wget curl apt-transport-https gnupg unzip zip && \
    apt-get autoremove -y

ENTRYPOINT [ "./idesyde" ]





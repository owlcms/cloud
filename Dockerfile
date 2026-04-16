# syntax=docker/dockerfile:1
#Stage 1
# initialize build and set base image for first stage
FROM maven:3.8.8-eclipse-temurin-17 AS stage1
# speed up Maven JVM a bit
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
ENV DEBIAN_FRONTEND=noninteractive
RUN apt update && apt install -y gettext-base && rm -rf /var/lib/apt/lists/*
# set working directory
WORKDIR /app
# copy just pom.xml
COPY pom.xml .
# go-offline using the pom.xml
RUN mvn dependency:go-offline package -P production
# copy your other files
COPY ./src ./src
COPY ./frontend ./src/main/frontend
COPY ./scripts ./scripts
COPY ./*.sh ./
COPY ./*.toml ./
# VERSION arg passed from deploy.sh via --build-arg VERSION=x.y.z
ARG VERSION=1.0.0
# compile the source code and package it in a jar file
RUN mvn clean package -P production -Dmaven.test.skip=true -Drevision=${VERSION}

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
ENV FLYCTL_INSTALL=/app/fly
# put required utilities in /app/fly/bin
RUN <<EOF
wget -O jq https://github.com/jqlang/jq/releases/download/jq-1.6/jq-linux64
chmod +x ./jq
mkdir /app/fly
curl -L https://fly.io/install.sh | sh
chmod +x /app/fly/bin
mv jq /app/fly/bin
EOF

# Copy the versioned jar and rename to fixed name for ENTRYPOINT
COPY --from=stage1 /app/target/fly-manager-*.jar /app/fly-manager.jar
COPY --from=stage1 /app/*.sh /app/
COPY --from=stage1 /app/scripts /app/scripts
COPY --from=stage1 /app/*.toml /app/
COPY --from=stage1 /usr/bin/envsubst /usr/bin
COPY --from=stage1 /app/src/main/resources/GeoLite2/GeoLite2-City.mmdb /app
EXPOSE 8080
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "fly-manager.jar", "-Xmx384m"]
# Stage 1: Build Application
FROM --platform=$BUILDPLATFORM gradle:8.11.1-jdk21 AS build

# Set Gradle options directly in the build
ENV GRADLE_OPTS="-Xmx512m -Xms256m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false -Dorg.gradle.workers.max=1"

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle installDist --no-daemon -x test

# Stage 3: Create the Runtime Image
FROM amazoncorretto:22 AS runtime
# install xargs (findutils)
RUN dnf install -y findutils && dnf clean all

EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/install/kasify/. /app

ENTRYPOINT ["/app/bin/kasify"]
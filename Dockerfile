FROM alpine:latest as packager

RUN apk --no-cache add openjdk11-jdk openjdk11-jmods git

ENV JAVA_MINIMAL="/opt/java-minimal"

# build minimal JRE
RUN /usr/lib/jvm/java-11-openjdk/bin/jlink \
    --verbose \
    --add-modules \
        java.base,java.sql,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,jdk.jartool,jdk.zipfs \
    --compress 2 --strip-debug --no-header-files --no-man-pages \
    --release-info="add:IMPLEMENTOR=radistao:IMPLEMENTOR_VERSION=radistao_JRE" \
    --output "$JAVA_MINIMAL"


COPY src /app/src
COPY lib /app/lib

RUN /usr/lib/jvm/java-11-openjdk/bin/javac -cp /app/lib/javaparser-core-3.14.159265359.jar:/app/lib/javax.json.jar:/app/lib/snakeyaml-1.25.jar -d /app/out /app/src/*.java

FROM alpine:latest

RUN apk --no-cache add git

ENV JAVA_HOME=/opt/java-minimal
ENV PATH="$PATH:$JAVA_HOME/bin"

COPY --from=packager "$JAVA_HOME" "$JAVA_HOME"
COPY --from=packager /app/out /app/bin
COPY --from=packager /app/lib /app/lib
RUN mkdir /app/out

COPY config.json /app/config.json

WORKDIR /app

ENTRYPOINT ["java","-cp", "bin:/app/lib/javaparser-core-3.14.159265359.jar:/app/lib/javax.json.jar:/app/lib/snakeyaml-1.25.jar","Main"]
#ENTRYPOINT ["/bin/sh"]

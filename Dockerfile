FROM ghcr.io/navikt/baseimages/temurin:11
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"
COPY build/libs/app*.jar app.jar
COPY pre-stop.sh /
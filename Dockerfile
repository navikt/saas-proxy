FROM gcr.io/distroless/java21
COPY build/libs/app*.jar app.jar
COPY pre-stop.sh /
ENTRYPOINT ["java", "-Dlogback.configurationFile=logback-remote.xml", "-jar", "/app.jar"]

FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY lib ./lib
COPY src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/omni_office-1.0-SNAPSHOT.jar /app/omni-office.jar
COPY --from=build /workspace/target/dependency /app/dependency
COPY --from=build /workspace/lib /app/lib
RUN useradd --system --uid 10001 --home /app omni && mkdir -p /data && chown -R omni:omni /app /data
USER omni
ENV OMNI_OFFICE_HOST=0.0.0.0 OMNI_OFFICE_PORT=8080 OMNI_OFFICE_DATA_ROOT=/data
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl --fail http://127.0.0.1:8080/health/ready || exit 1
ENTRYPOINT ["java", "-cp", "/app/omni-office.jar:/app/dependency/*:/app/lib/*", "cn.bugstack.application.external.http.McpHttpServerMain"]

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先单独拷贝 pom 预热依赖缓存层
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r vax && useradd -r -g vax -u 1001 vax
WORKDIR /app
COPY --from=build /app/target/vaccine-platform.jar app.jar
USER vax
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=15 \
  CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

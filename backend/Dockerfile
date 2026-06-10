# 멀티스테이지 빌드 — 빌드 환경과 런타임 환경 분리로 이미지 최소화
# Stage 1: Gradle 빌드
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 의존성 레이어를 소스와 분리 — build.gradle 변경 없으면 캐시 재사용
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: 런타임 이미지 — JRE만 포함 (JDK 제외, ~200MB 절감)
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

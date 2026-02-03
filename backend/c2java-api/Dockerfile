# ============================================
# C2JAVA Backend API Dockerfile
# Spring Boot 3.2.5 / Java 21
# ============================================

# Build Stage
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Gradle 캐싱을 위한 의존성 파일 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 다운로드 (캐싱 활용)
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# 빌드
RUN gradle bootJar --no-daemon

# ============================================
# Runtime Stage
# ============================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/c2java-api.jar app.jar

# 필요한 디렉토리 생성
RUN mkdir -p /app/workspace /app/output /app/logs /app/config

# 개발 환경에서 Docker 소켓 접근을 위해 root로 실행

# 환경변수
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SPRING_PROFILES_ACTIVE=prod

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health || exit 1

# 포트 노출
EXPOSE 8080

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

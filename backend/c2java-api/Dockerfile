# ============================================
# C2JAVA Backend API Dockerfile
# Spring Boot 3.2.5 / Java 21
# ============================================

# Build Stage
FROM gradle:8.5-jdk21-alpine AS builder

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
FROM eclipse-temurin:21-jre-alpine

# 보안: 비root 사용자 생성
RUN addgroup -g 1001 -S c2java && \
    adduser -u 1001 -S c2java -G c2java

WORKDIR /app

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/c2java-api.jar app.jar

# 필요한 디렉토리 생성
RUN mkdir -p /app/workspace /app/output /app/logs /app/config && \
    chown -R c2java:c2java /app

# 비root 사용자로 전환
USER c2java

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

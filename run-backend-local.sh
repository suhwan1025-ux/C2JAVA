#!/bin/bash
# Backend 로컬 실행 스크립트 (Cursor CLI 사용 시)

echo "========================================"
echo "C2JAVA Backend - Local Execution"
echo "For Cursor CLI Integration"
echo "========================================"

# 환경변수 확인 (주석 처리 - 로컬 실행 시 강제 설정)
# ACTIVE_CLI_TOOL=$(grep "^ACTIVE_CLI_TOOL=" config/env/.env.internal | cut -d'=' -f2)
# echo "현재 활성 CLI 도구: $ACTIVE_CLI_TOOL"

# if [ "$ACTIVE_CLI_TOOL" != "cursor" ]; then
#     echo "⚠️  경고: ACTIVE_CLI_TOOL이 'cursor'가 아닙니다."
#     echo "Cursor CLI를 사용하려면 어드민 페이지에서 외부망 프리셋을 적용하세요."
#     read -p "계속 진행하시겠습니까? (y/N): " confirm
#     if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
#         exit 0
#     fi
# fi

# Docker 서비스 확인
echo ""
echo "Docker 서비스 상태 확인 중..."
POSTGRES_RUNNING=$(docker ps | grep c2java-postgres | wc -l)
AIRFLOW_RUNNING=$(docker ps | grep c2java-airflow | wc -l)

if [ $POSTGRES_RUNNING -eq 0 ]; then
    echo "❌ PostgreSQL이 실행되지 않았습니다."
    echo "Docker Compose를 먼저 시작하세요:"
    echo "  cd docker/compose && docker-compose up -d postgres"
    exit 1
fi

if [ $AIRFLOW_RUNNING -eq 0 ]; then
    echo "⚠️  Airflow가 실행되지 않았습니다."
    echo "Airflow를 시작하려면:"
    echo "  cd docker/compose && docker-compose up -d airflow-webserver airflow-scheduler"
fi

# Docker Backend 중지
echo ""
echo "Docker Backend 중지 중..."
cd docker/compose
docker-compose stop backend 2>/dev/null
cd ../..

# 환경변수 설정
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/c2java
export SPRING_DATASOURCE_USERNAME=c2java_user
export SPRING_DATASOURCE_PASSWORD=c2java_password

export AIRFLOW_URL=http://localhost:8081
export AIRFLOW_USERNAME=admin
export AIRFLOW_PASSWORD=admin

export WORKSPACE_PATH=$(pwd)/workspace
export ENV_FILE_PATH=$(pwd)/config/env/.env.internal

# CLI 설정
export ACTIVE_CLI_TOOL=cursor
export CURSOR_CLI_ENABLED=true
export CURSOR_AGENT_PATH=/Users/dongsoo/.local/bin/agent
# CURSOR_CLI_MODEL은 .env.internal 파일에서 읽어옴 (하드코딩하지 않음)
unset CURSOR_CLI_MODEL

# CLI Service URL (로컬 실행 시)
export CLI_SERVICE_URL=http://localhost:8001
export CONVERSION_RULES_DIR=/Users/dongsoo/Desktop/C2JAVA/config/rules

echo ""
echo "✅ 환경변수 설정 완료"
echo "   - Database: localhost:5432/c2java"
echo "   - Airflow: localhost:8081"
echo "   - Workspace: $WORKSPACE_PATH"
echo "   - Cursor Agent: $CURSOR_AGENT_PATH"
echo ""

# Cursor agent 확인
if [ ! -f "$CURSOR_AGENT_PATH" ]; then
    echo "❌ Cursor agent를 찾을 수 없습니다: $CURSOR_AGENT_PATH"
    exit 1
fi

echo "✅ Cursor agent 확인됨"
$CURSOR_AGENT_PATH status 2>&1 | head -5

# JAR 빌드
echo ""
echo "Backend JAR 빌드 중..."
cd backend/c2java-api
./gradlew clean bootJar

if [ $? -ne 0 ]; then
    echo "❌ 빌드 실패"
    exit 1
fi

echo ""
echo "========================================"
echo "Backend 시작 중..."
echo "Port: 8080"
echo "========================================"
echo ""
echo "✅ 로그를 보려면: tail -f backend.log"
echo "✅ 종료하려면: ./stop-backend-local.sh"
echo ""

# Backend 백그라운드 실행 (환경변수 명시적 전달)
nohup java -Dspring.profiles.active=dev \
  -Dconversion.rules-dir=$CONVERSION_RULES_DIR \
  -Dconversion.workspace-dir=$(pwd)/../../workspace \
  -Dconversion.output-dir=$(pwd)/../../output \
  -Dairflow.dags-dir=$(pwd)/../../airflow/dags \
  -Dairflow.url="$AIRFLOW_URL" \
  -Dairflow.username="$AIRFLOW_USERNAME" \
  -Dairflow.password="$AIRFLOW_PASSWORD" \
  -Dspring.datasource.url="$SPRING_DATASOURCE_URL" \
  -Dspring.datasource.username="$SPRING_DATASOURCE_USERNAME" \
  -Dspring.datasource.password="$SPRING_DATASOURCE_PASSWORD" \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dcli.service.url="$CLI_SERVICE_URL" \
  -jar build/libs/c2java-api.jar > ../../backend.log 2>&1 &
BACKEND_PID=$!
echo $BACKEND_PID > ../../backend.pid

echo "Backend PID: $BACKEND_PID"
echo ""
echo "Backend가 백그라운드에서 시작되었습니다."
echo ""

# 시작 확인 (10초 대기)
echo "Backend 시작 확인 중..."
for i in {1..10}; do
    sleep 1
    if curl -s http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
        echo "✅ Backend가 성공적으로 시작되었습니다!"
        echo ""
        echo "접속 URL:"
        echo "  - API: http://localhost:8080/api"
        echo "  - Health: http://localhost:8080/api/actuator/health"
        echo ""
        exit 0
    fi
    echo -n "."
done

echo ""
echo "⚠️  Backend 시작을 확인할 수 없습니다."
echo "로그를 확인하세요: tail -f ../../backend.log"

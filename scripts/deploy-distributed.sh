#!/bin/bash
# ============================================
# C2JAVA 분산 배포 스크립트
# CLI, MCP, Web 서버 분리 배포
# 여러 사용자 동시 접속 지원
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

IMAGE_TAR=${1:-"c2java-images-latest.tar"}
VERSION=${2:-"latest"}
COMPOSE_FILE="docker/compose/docker-compose.distributed.yml"

echo "🚀 C2JAVA 분산 환경 배포 시작"
echo ""
echo "   버전: $VERSION"
echo "   Compose 파일: $COMPOSE_FILE"
echo ""

# Docker 실행 확인
if ! docker info &> /dev/null; then
    echo "❌ Docker가 실행되고 있지 않습니다."
    exit 1
fi

# Docker 이미지 로드 (tar 파일이 있는 경우)
if [ -f "$IMAGE_TAR" ]; then
    echo "🔄 Docker 이미지 로드 중..."
    docker load -i $IMAGE_TAR
    echo "✅ 이미지 로드 완료"
fi

# 환경변수 파일 확인
if [ ! -f "config/env/.env" ]; then
    if [ -f "config/env/.env.internal" ]; then
        echo "📋 내부망 환경변수 파일 복사..."
        cp config/env/.env.internal config/env/.env
    else
        echo "📋 기본 환경변수 파일 복사..."
        cp config/env/.env.example config/env/.env
    fi
fi

# 환경변수 로드
export $(grep -v '^#' config/env/.env | xargs)
export VERSION=$VERSION

# Nginx 설정 디렉토리 생성
mkdir -p docker/compose/nginx/conf.d

# 기존 컨테이너 정리
echo ""
echo "🧹 기존 컨테이너 정리..."
docker compose -f $COMPOSE_FILE down --remove-orphans 2>/dev/null || true

# 볼륨 생성 (데이터 보존)
echo ""
echo "📁 볼륨 확인..."
docker volume create c2java_postgres_data 2>/dev/null || true
docker volume create c2java_redis_data 2>/dev/null || true
docker volume create c2java_workspace_data 2>/dev/null || true
docker volume create c2java_output_data 2>/dev/null || true

# Docker Compose 실행
echo ""
echo "🐳 서비스 시작..."
docker compose -f $COMPOSE_FILE up -d

# 상태 확인
echo ""
echo "⏳ 서비스 상태 확인 중..."
sleep 15

docker compose -f $COMPOSE_FILE ps

# 헬스체크
echo ""
echo "🏥 헬스체크..."

# 데이터베이스 대기
echo "   PostgreSQL 대기 중..."
for i in {1..30}; do
    if docker compose -f $COMPOSE_FILE exec -T postgres pg_isready -U ${DB_USER:-c2java_user} > /dev/null 2>&1; then
        echo "   ✅ PostgreSQL 준비 완료"
        break
    fi
    sleep 2
done

# Redis 대기
echo "   Redis 대기 중..."
for i in {1..10}; do
    if docker compose -f $COMPOSE_FILE exec -T redis redis-cli ping > /dev/null 2>&1; then
        echo "   ✅ Redis 준비 완료"
        break
    fi
    sleep 2
done

# 백엔드 API 대기
echo "   백엔드 API 대기 중..."
for i in {1..20}; do
    if curl -s -f http://localhost:${SERVER_PORT:-8080}/api/actuator/health > /dev/null 2>&1; then
        echo "   ✅ 백엔드 API 준비 완료"
        break
    fi
    echo "   ... 대기 중 ($i/20)"
    sleep 3
done

# CLI 서비스 대기
echo "   CLI 서비스 대기 중..."
for i in {1..10}; do
    if curl -s -f http://localhost:8083/health > /dev/null 2>&1; then
        echo "   ✅ CLI 서비스 준비 완료"
        break
    fi
    sleep 2
done

# MCP 서버 대기
echo "   MCP 서버 대기 중..."
for i in {1..10}; do
    if curl -s -f http://localhost:8082/health > /dev/null 2>&1; then
        echo "   ✅ MCP 서버 준비 완료"
        break
    fi
    sleep 2
done

echo ""
echo "============================================"
echo "✅ C2JAVA 분산 환경 배포 완료!"
echo "============================================"
echo ""
echo "📍 접속 URL:"
echo "   - 웹 UI:      http://$(hostname -f 2>/dev/null || echo 'localhost'):${FRONTEND_PORT:-3000}"
echo "   - API 서버:   http://$(hostname -f 2>/dev/null || echo 'localhost'):${SERVER_PORT:-8080}/api"
echo "   - CLI 서비스: http://$(hostname -f 2>/dev/null || echo 'localhost'):8083"
echo "   - MCP 서버:   http://$(hostname -f 2>/dev/null || echo 'localhost'):8082"
echo "   - Airflow:    http://$(hostname -f 2>/dev/null || echo 'localhost'):${AIRFLOW_PORT:-8081}"
echo "   - Grafana:    http://$(hostname -f 2>/dev/null || echo 'localhost'):${GRAFANA_PORT:-3001}"
echo ""
echo "📋 기본 계정:"
echo "   - 관리자: admin / admin123 (최초 로그인 후 변경 필요)"
echo ""
echo "🔧 환경변수 변경 방법:"
echo "   1. 웹 UI 관리자 페이지에서 변경 (권장)"
echo "   2. config/env/.env 파일 수정 후 재시작"
echo ""
echo "📋 관리 명령어:"
echo "   - 로그 보기: docker compose -f $COMPOSE_FILE logs -f [서비스명]"
echo "   - 재시작:    docker compose -f $COMPOSE_FILE restart [서비스명]"
echo "   - 중지:      docker compose -f $COMPOSE_FILE down"
echo "   - 상태 확인: docker compose -f $COMPOSE_FILE ps"

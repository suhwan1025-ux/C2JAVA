#!/bin/bash
# ============================================
# C2JAVA 내부망 배포 스크립트
# 폐쇄망 환경용
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

IMAGE_TAR=${1:-"c2java-images-latest.tar"}
VERSION=${2:-"latest"}

echo "🚀 C2JAVA 내부망 배포 시작"
echo ""

# Docker 실행 확인
if ! docker info &> /dev/null; then
    echo "❌ Docker가 실행되고 있지 않습니다."
    exit 1
fi

# Docker 이미지 로드
if [ -f "$IMAGE_TAR" ]; then
    echo "🔄 Docker 이미지 로드 중..."
    docker load -i $IMAGE_TAR
    echo "✅ 이미지 로드 완료"
else
    echo "❌ 이미지 파일을 찾을 수 없습니다: $IMAGE_TAR"
    exit 1
fi

# 환경변수 파일 확인
if [ ! -f "config/env/.env.internal" ]; then
    echo "❌ 내부망 환경변수 파일이 없습니다: config/env/.env.internal"
    echo "   config/env/.env.example을 참고하여 .env.internal 파일을 생성하세요."
    exit 1
fi

# 환경변수 파일 복사
echo ""
echo "📋 환경변수 파일 설정..."
cp config/env/.env.internal config/env/.env
echo "✅ 환경변수 설정 완료"

# 환경변수 로드
export $(grep -v '^#' config/env/.env | xargs)
export VERSION=$VERSION

# 기존 컨테이너 정리
echo ""
echo "🧹 기존 컨테이너 정리..."
docker compose -f docker/compose/docker-compose.prod.yml down --remove-orphans 2>/dev/null || true

# Docker Compose 실행
echo ""
echo "🐳 서비스 시작..."
docker compose -f docker/compose/docker-compose.prod.yml up -d

# 상태 확인
echo ""
echo "⏳ 서비스 상태 확인 중..."
sleep 30

docker compose -f docker/compose/docker-compose.prod.yml ps

# 헬스체크
echo ""
echo "🏥 헬스체크..."
for i in {1..10}; do
    if curl -s -f http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
        echo "✅ 백엔드 API 정상"
        break
    fi
    echo "   백엔드 대기 중... ($i/10)"
    sleep 5
done

echo ""
echo "============================================"
echo "✅ C2JAVA 배포 완료!"
echo "============================================"
echo ""
echo "📍 접속 URL:"
echo "   - 프론트엔드: http://$(hostname -f 2>/dev/null || echo 'localhost'):${FRONTEND_PORT:-3000}"
echo "   - 백엔드 API: http://$(hostname -f 2>/dev/null || echo 'localhost'):${SERVER_PORT:-8080}/api"
echo "   - Swagger UI: http://$(hostname -f 2>/dev/null || echo 'localhost'):${SERVER_PORT:-8080}/api/swagger-ui.html"
echo "   - Airflow: http://$(hostname -f 2>/dev/null || echo 'localhost'):${AIRFLOW_PORT:-8081}"
echo "   - Grafana: http://$(hostname -f 2>/dev/null || echo 'localhost'):${GRAFANA_PORT:-3001}"
echo ""
echo "📋 관리 명령어:"
echo "   - 로그 보기: docker compose -f docker/compose/docker-compose.prod.yml logs -f"
echo "   - 서비스 중지: docker compose -f docker/compose/docker-compose.prod.yml down"
echo "   - 서비스 재시작: docker compose -f docker/compose/docker-compose.prod.yml restart"
echo "   - 상태 확인: docker compose -f docker/compose/docker-compose.prod.yml ps"

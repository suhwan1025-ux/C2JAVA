#!/bin/bash
# ============================================
# C2JAVA Docker 개발 환경 실행 스크립트
# 로컬에 의존성 설치 없이 Docker만으로 실행
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "🐳 C2JAVA Docker 개발 환경 시작..."
echo ""

# Docker 실행 확인
if ! docker info &> /dev/null; then
    echo "❌ Docker가 실행되고 있지 않습니다."
    echo "   Docker Desktop을 먼저 실행해주세요."
    exit 1
fi

# 환경변수 파일 확인
if [ ! -f "config/env/.env" ]; then
    echo "📋 환경변수 파일 생성 중..."
    cp config/env/.env.example config/env/.env
fi

# 환경변수 로드
export $(grep -v '^#' config/env/.env | xargs)

# Docker Compose 실행
echo "📦 Docker 컨테이너 시작 중..."
docker compose -f docker/compose/docker-compose.dev.yml up --build -d

# 상태 확인
echo ""
echo "⏳ 서비스 시작 대기 중..."
sleep 10

docker compose -f docker/compose/docker-compose.dev.yml ps

echo ""
echo "============================================"
echo "✅ C2JAVA 개발 환경이 시작되었습니다!"
echo "============================================"
echo ""
echo "📍 접속 URL:"
echo "   - 프론트엔드: http://localhost:3000"
echo "   - 백엔드 API: http://localhost:8080/api"
echo "   - Swagger UI: http://localhost:8080/api/swagger-ui.html"
echo "   - Grafana: http://localhost:3001 (admin/admin)"
echo ""
echo "📋 유용한 명령어:"
echo "   - 로그 보기: docker compose -f docker/compose/docker-compose.dev.yml logs -f"
echo "   - 중지: docker compose -f docker/compose/docker-compose.dev.yml down"
echo "   - 재시작: docker compose -f docker/compose/docker-compose.dev.yml restart"

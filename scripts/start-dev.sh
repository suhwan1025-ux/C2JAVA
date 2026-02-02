#!/bin/bash
# ============================================
# C2JAVA 개발 환경 시작 스크립트
# ============================================

set -e

echo "🚀 C2JAVA 개발 환경 시작..."

# 환경변수 로드
if [ -f "config/env/.env" ]; then
    export $(cat config/env/.env | grep -v '^#' | xargs)
else
    echo "⚠️  환경변수 파일이 없습니다. config/env/.env.example을 복사하세요."
    cp config/env/.env.example config/env/.env
    export $(cat config/env/.env | grep -v '^#' | xargs)
fi

# Docker Compose로 인프라 시작
echo "📦 Docker 인프라 시작..."
docker-compose -f docker/compose/docker-compose.yml up -d postgres grafana

# 데이터베이스 준비 대기
echo "⏳ 데이터베이스 준비 대기 중..."
sleep 10

# 백엔드 시작 (백그라운드)
echo "🔧 백엔드 시작..."
cd backend/c2java-api
./gradlew bootRun &
BACKEND_PID=$!
cd ../..

# 프론트엔드 시작 (백그라운드)
echo "🎨 프론트엔드 시작..."
cd frontend/c2java-web
npm install
npm run dev &
FRONTEND_PID=$!
cd ../..

echo ""
echo "✅ C2JAVA 개발 환경이 시작되었습니다!"
echo ""
echo "📍 접속 URL:"
echo "   - 프론트엔드: http://localhost:3000"
echo "   - 백엔드 API: http://localhost:8080/api"
echo "   - Swagger UI: http://localhost:8080/api/swagger-ui.html"
echo "   - Grafana: http://localhost:3001"
echo ""
echo "종료하려면 Ctrl+C를 누르세요."

# 프로세스 종료 처리
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; docker-compose -f docker/compose/docker-compose.yml down" EXIT

wait

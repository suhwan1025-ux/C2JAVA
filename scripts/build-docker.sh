#!/bin/bash
# ============================================
# C2JAVA Docker 이미지 빌드 스크립트
# 폐쇄망 배포용
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

VERSION=${1:-"latest"}
REGISTRY=${2:-""}
SAVE_TAR=${3:-"false"}

echo "🔨 C2JAVA Docker 이미지 빌드 시작 (버전: $VERSION)"
echo ""

# Docker 실행 확인
if ! docker info &> /dev/null; then
    echo "❌ Docker가 실행되고 있지 않습니다."
    echo "   Docker Desktop을 먼저 실행해주세요."
    exit 1
fi

# 백엔드 빌드
echo "📦 [1/4] 백엔드 이미지 빌드..."
docker build -t c2java-backend:$VERSION -f backend/c2java-api/Dockerfile backend/c2java-api
echo "✅ 백엔드 빌드 완료"
echo ""

# 프론트엔드 빌드
echo "🎨 [2/4] 프론트엔드 이미지 빌드..."
docker build -t c2java-frontend:$VERSION -f frontend/c2java-web/Dockerfile frontend/c2java-web
echo "✅ 프론트엔드 빌드 완료"
echo ""

# CLI 서비스 빌드
echo "⚙️  [3/4] CLI 서비스 이미지 빌드..."
docker build -t c2java-cli:$VERSION -f cli/Dockerfile cli
echo "✅ CLI 서비스 빌드 완료"
echo ""

# MCP 서버 빌드
echo "🔗 [4/4] MCP 서버 이미지 빌드..."
docker build -t c2java-mcp:$VERSION -f mcp/Dockerfile mcp
echo "✅ MCP 서버 빌드 완료"
echo ""

# 레지스트리 지정된 경우 태깅
if [ -n "$REGISTRY" ]; then
    echo "📤 이미지 태깅..."
    docker tag c2java-backend:$VERSION $REGISTRY/c2java-backend:$VERSION
    docker tag c2java-frontend:$VERSION $REGISTRY/c2java-frontend:$VERSION
    docker tag c2java-cli:$VERSION $REGISTRY/c2java-cli:$VERSION
    docker tag c2java-mcp:$VERSION $REGISTRY/c2java-mcp:$VERSION
    echo "✅ 태깅 완료"
fi

echo ""
echo "============================================"
echo "✅ Docker 이미지 빌드 완료!"
echo "============================================"
echo ""
echo "생성된 이미지:"
docker images | grep c2java | head -10

# 이미지 저장 (폐쇄망 배포용)
if [ "$SAVE_TAR" = "true" ] || [ "$SAVE_TAR" = "yes" ]; then
    echo ""
    echo "📁 이미지를 tar 파일로 저장 중..."
    
    # 기본 이미지도 함께 저장
    docker pull postgres:15-alpine
    docker pull grafana/grafana:10.3.1
    docker pull apache/airflow:2.8.1-python3.11
    docker pull nginx:alpine
    
    docker save \
        c2java-backend:$VERSION \
        c2java-frontend:$VERSION \
        c2java-cli:$VERSION \
        c2java-mcp:$VERSION \
        postgres:15-alpine \
        grafana/grafana:10.3.1 \
        apache/airflow:2.8.1-python3.11 \
        nginx:alpine \
        -o c2java-images-$VERSION.tar
    
    echo "✅ 저장 완료: c2java-images-$VERSION.tar"
    ls -lh c2java-images-$VERSION.tar
fi

echo ""
echo "📋 폐쇄망 배포를 위한 다음 단계:"
echo "   1. tar 파일 저장: ./scripts/build-docker.sh $VERSION \"\" true"
echo "   2. 내부망으로 파일 전송: c2java-images-$VERSION.tar"
echo "   3. 내부망에서 배포: ./scripts/deploy-internal.sh c2java-images-$VERSION.tar"

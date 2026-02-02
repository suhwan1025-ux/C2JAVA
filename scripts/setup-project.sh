#!/bin/bash
# ============================================
# C2JAVA 프로젝트 설정 스크립트
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "🚀 C2JAVA 프로젝트 설정을 시작합니다..."
echo ""

# 환경변수 파일 생성
echo "📋 환경변수 파일 설정..."
if [ ! -f "config/env/.env" ]; then
    cp config/env/.env.example config/env/.env
    echo "✅ config/env/.env 파일 생성됨"
else
    echo "✅ config/env/.env 파일 이미 존재"
fi

# 백엔드 설정
echo ""
echo "☕ 백엔드 프로젝트 설정..."
cd "$PROJECT_ROOT/backend/c2java-api"

# Gradle Wrapper 생성
echo "   Gradle Wrapper 생성 중..."
gradle wrapper --gradle-version 8.5

# 의존성 다운로드
echo "   의존성 다운로드 중..."
./gradlew dependencies --no-daemon

# 빌드 테스트
echo "   빌드 테스트 중..."
./gradlew build -x test --no-daemon

echo "✅ 백엔드 설정 완료"

# 프론트엔드 설정
echo ""
echo "🎨 프론트엔드 프로젝트 설정..."
cd "$PROJECT_ROOT/frontend/c2java-web"

# npm 의존성 설치
echo "   npm 의존성 설치 중..."
npm install

# 빌드 테스트
echo "   빌드 테스트 중..."
npm run build

echo "✅ 프론트엔드 설정 완료"

# CLI 서비스 설정
echo ""
echo "⚙️  CLI 서비스 설정..."
cd "$PROJECT_ROOT/cli"

# Python 가상환경 생성
echo "   Python 가상환경 생성 중..."
python3 -m venv venv

# 의존성 설치
echo "   Python 의존성 설치 중..."
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
deactivate

echo "✅ CLI 서비스 설정 완료"

# MCP 서버 설정
echo ""
echo "🔗 MCP 서버 설정..."
cd "$PROJECT_ROOT/mcp"

# Python 가상환경 생성
echo "   Python 가상환경 생성 중..."
python3 -m venv venv

# 의존성 설치
echo "   Python 의존성 설치 중..."
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
deactivate

echo "✅ MCP 서버 설정 완료"

# 스크립트 실행 권한 부여
echo ""
echo "📜 스크립트 실행 권한 설정..."
cd "$PROJECT_ROOT"
chmod +x scripts/*.sh

echo "✅ 스크립트 권한 설정 완료"

echo ""
echo "============================================"
echo "✅ 프로젝트 설정이 완료되었습니다!"
echo "============================================"
echo ""
echo "다음 단계:"
echo "   1. 환경변수 수정: vim config/env/.env"
echo "   2. Docker 빌드: ./scripts/build-docker.sh"
echo "   3. 개발 서버 시작: ./scripts/start-dev.sh"

#!/bin/bash
# ============================================
# C2JAVA 빠른 시작 스크립트
# Docker 없이 로컬에서 바로 실행
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "🚀 C2JAVA 빠른 시작"
echo ""

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 환경 확인 함수
check_command() {
    if command -v $1 &> /dev/null; then
        echo -e "${GREEN}✓${NC} $1 설치됨"
        return 0
    else
        echo -e "${RED}✗${NC} $1 미설치"
        return 1
    fi
}

echo "📋 환경 확인 중..."
echo ""

MISSING=0

# Java 확인
if ! check_command java; then
    echo "   brew install openjdk@21"
    MISSING=1
fi

# Node.js 확인
if ! check_command node; then
    echo "   brew install node@20"
    MISSING=1
fi

# npm 확인
if ! check_command npm; then
    MISSING=1
fi

echo ""

if [ $MISSING -eq 1 ]; then
    echo -e "${YELLOW}⚠️  필수 도구가 설치되지 않았습니다.${NC}"
    echo ""
    echo "다음 명령어로 설치하세요:"
    echo ""
    echo "  # Homebrew 설치 (없는 경우)"
    echo '  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
    echo ""
    echo "  # 필수 도구 설치"
    echo "  brew install openjdk@21 node@20"
    echo ""
    echo "  # 환경변수 설정"
    echo '  echo '\''export JAVA_HOME="/opt/homebrew/opt/openjdk@21"'\'' >> ~/.zshrc'
    echo '  echo '\''export PATH="$JAVA_HOME/bin:$PATH"'\'' >> ~/.zshrc'
    echo '  echo '\''export PATH="/opt/homebrew/opt/node@20/bin:$PATH"'\'' >> ~/.zshrc'
    echo "  source ~/.zshrc"
    echo ""
    exit 1
fi

echo -e "${GREEN}✓ 모든 필수 도구가 설치되어 있습니다.${NC}"
echo ""

# 환경변수 파일 확인
if [ ! -f "config/env/.env" ]; then
    echo "📋 환경변수 파일 생성..."
    cp config/env/.env.example config/env/.env
fi

# 프론트엔드 의존성 설치
echo "📦 프론트엔드 의존성 설치 중..."
cd "$PROJECT_ROOT/frontend/c2java-web"
if [ ! -d "node_modules" ]; then
    npm install
fi

# 백엔드 Gradle Wrapper 확인
cd "$PROJECT_ROOT/backend/c2java-api"
if [ ! -f "gradlew" ] || [ ! -x "gradlew" ]; then
    echo "📦 Gradle Wrapper 생성 필요..."
    echo "   gradle wrapper 명령어를 실행하거나"
    echo "   Docker 환경을 사용하세요."
fi

echo ""
echo "============================================"
echo "🎉 준비 완료!"
echo "============================================"
echo ""
echo "다음 단계:"
echo ""
echo "1. 백엔드 실행 (새 터미널):"
echo "   cd $PROJECT_ROOT/backend/c2java-api"
echo "   ./gradlew bootRun"
echo ""
echo "2. 프론트엔드 실행 (새 터미널):"
echo "   cd $PROJECT_ROOT/frontend/c2java-web"
echo "   npm run dev"
echo ""
echo "3. 웹 브라우저에서 접속:"
echo "   http://localhost:3000"
echo ""
echo "4. 테스트 계정:"
echo "   관리자: admin / admin123"
echo ""

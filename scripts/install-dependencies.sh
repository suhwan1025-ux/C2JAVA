#!/bin/bash
# ============================================
# C2JAVA 개발 환경 설치 스크립트 (macOS)
# ============================================

set -e

echo "🚀 C2JAVA 개발 환경 설치를 시작합니다..."
echo ""

# Homebrew 설치 확인
if ! command -v brew &> /dev/null; then
    echo "📦 Homebrew 설치 중..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    
    # Apple Silicon Mac용 PATH 설정
    if [[ $(uname -m) == 'arm64' ]]; then
        echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
else
    echo "✅ Homebrew 이미 설치됨"
fi

echo ""
echo "📦 필수 패키지 설치 중..."

# Java 21 LTS 설치
echo ""
echo "☕ Java 21 LTS 설치 중..."
brew install openjdk@21

# Java 환경변수 설정
JAVA_HOME_PATH="/opt/homebrew/opt/openjdk@21"
if [[ $(uname -m) == 'x86_64' ]]; then
    JAVA_HOME_PATH="/usr/local/opt/openjdk@21"
fi

if ! grep -q "JAVA_HOME" ~/.zshrc 2>/dev/null; then
    echo "" >> ~/.zshrc
    echo "# Java 21" >> ~/.zshrc
    echo "export JAVA_HOME=\"$JAVA_HOME_PATH\"" >> ~/.zshrc
    echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
fi

export JAVA_HOME="$JAVA_HOME_PATH"
export PATH="$JAVA_HOME/bin:$PATH"

# 심볼릭 링크 생성 (시스템 Java로 사용)
sudo ln -sfn "$JAVA_HOME_PATH/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || true

echo "✅ Java 21 설치 완료"
java -version

# Python 3.11 설치
echo ""
echo "🐍 Python 3.11 설치 중..."
brew install python@3.11

# Python 환경변수 설정
if ! grep -q "python@3.11" ~/.zshrc 2>/dev/null; then
    echo "" >> ~/.zshrc
    echo "# Python 3.11" >> ~/.zshrc
    echo 'export PATH="/opt/homebrew/opt/python@3.11/bin:$PATH"' >> ~/.zshrc
fi

echo "✅ Python 3.11 설치 완료"
python3.11 --version

# Node.js 20 LTS 설치
echo ""
echo "📗 Node.js 20 LTS 설치 중..."
brew install node@20

# Node.js 환경변수 설정
if ! grep -q "node@20" ~/.zshrc 2>/dev/null; then
    echo "" >> ~/.zshrc
    echo "# Node.js 20" >> ~/.zshrc
    echo 'export PATH="/opt/homebrew/opt/node@20/bin:$PATH"' >> ~/.zshrc
fi

export PATH="/opt/homebrew/opt/node@20/bin:$PATH"

echo "✅ Node.js 20 설치 완료"
node --version
npm --version

# Gradle 설치
echo ""
echo "🔧 Gradle 설치 중..."
brew install gradle

echo "✅ Gradle 설치 완료"
gradle --version | head -3

# Docker Desktop 설치
echo ""
echo "🐳 Docker Desktop 설치 중..."
if ! command -v docker &> /dev/null; then
    brew install --cask docker
    echo "⚠️  Docker Desktop을 수동으로 실행해주세요: /Applications/Docker.app"
    echo "   Docker Desktop 실행 후 이 스크립트를 다시 실행하세요."
else
    echo "✅ Docker 이미 설치됨"
    docker --version
fi

# Git 설치 (보통 Xcode CLT와 함께 설치됨)
echo ""
echo "📚 Git 설치 확인..."
if ! command -v git &> /dev/null; then
    brew install git
fi
echo "✅ Git 설치 완료"
git --version

echo ""
echo "============================================"
echo "✅ 모든 의존성 설치가 완료되었습니다!"
echo "============================================"
echo ""
echo "설치된 버전:"
echo "  - Java: $(java -version 2>&1 | head -1)"
echo "  - Python: $(python3.11 --version)"
echo "  - Node.js: $(node --version)"
echo "  - npm: $(npm --version)"
echo "  - Gradle: $(gradle --version 2>&1 | grep Gradle)"
echo "  - Docker: $(docker --version 2>/dev/null || echo '설치됨 (실행 필요)')"
echo ""
echo "⚠️  터미널을 새로 열거나 다음 명령어를 실행하세요:"
echo "   source ~/.zshrc"
echo ""
echo "다음 단계:"
echo "   ./scripts/setup-project.sh"

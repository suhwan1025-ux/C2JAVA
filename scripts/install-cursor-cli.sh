#!/bin/bash
# ============================================
# Cursor CLI 설치 스크립트
# ============================================

set -e

echo "=========================================="
echo "Cursor CLI 설치 시작"
echo "=========================================="

# 운영체제 확인
OS=$(uname -s)
ARCH=$(uname -m)

echo "운영체제: $OS $ARCH"

# Cursor CLI 다운로드 URL (실제 URL은 Cursor 공식 문서 참조)
# 예시 URL - 실제 환경에 맞게 수정 필요
CURSOR_CLI_URL=""

if [[ "$OS" == "Darwin" ]]; then
    # macOS
    if [[ "$ARCH" == "arm64" ]]; then
        CURSOR_CLI_URL="https://download.cursor.sh/cli/cursor-cli-darwin-arm64"
    else
        CURSOR_CLI_URL="https://download.cursor.sh/cli/cursor-cli-darwin-x64"
    fi
elif [[ "$OS" == "Linux" ]]; then
    # Linux
    if [[ "$ARCH" == "x86_64" ]]; then
        CURSOR_CLI_URL="https://download.cursor.sh/cli/cursor-cli-linux-x64"
    elif [[ "$ARCH" == "aarch64" ]]; then
        CURSOR_CLI_URL="https://download.cursor.sh/cli/cursor-cli-linux-arm64"
    fi
fi

# 설치 디렉토리
INSTALL_DIR="/usr/local/bin"
CLI_PATH="$INSTALL_DIR/cursor"

# 이미 설치되어 있는지 확인
if command -v cursor &> /dev/null; then
    CURRENT_VERSION=$(cursor --version 2>/dev/null || echo "unknown")
    echo "Cursor CLI가 이미 설치되어 있습니다: $CURRENT_VERSION"
    read -p "다시 설치하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "설치를 취소합니다."
        exit 0
    fi
fi

# Cursor CLI 다운로드
echo "Cursor CLI 다운로드 중..."
if [[ -n "$CURSOR_CLI_URL" ]]; then
    echo "URL: $CURSOR_CLI_URL"
    # curl -fsSL "$CURSOR_CLI_URL" -o /tmp/cursor-cli
    # sudo mv /tmp/cursor-cli "$CLI_PATH"
    # sudo chmod +x "$CLI_PATH"
    echo "[참고] 실제 Cursor CLI 다운로드 URL은 Cursor 공식 문서를 참조하세요"
else
    echo "지원하지 않는 플랫폼입니다: $OS $ARCH"
    exit 1
fi

# 대안: npm을 통한 설치 (있는 경우)
echo ""
echo "=========================================="
echo "대안: Cursor는 현재 별도 CLI를 제공하지 않습니다"
echo "=========================================="
echo ""
echo "외부망 환경에서는 다음 CLI 도구를 사용할 수 있습니다:"
echo ""
echo "1. AIDER (AI Pair Programming)"
echo "   설치: pip install aider-chat"
echo "   사용: aider --model gpt-4 file.c"
echo ""
echo "2. Fabric (AI Pattern Framework)"
echo "   설치: pip install fabric-ai"
echo "   사용: fabric --pattern analyze_code < file.c"
echo ""
echo "3. OpenAI CLI (직접 API 호출)"
echo "   설치: pip install openai"
echo "   사용: Python 스크립트로 API 호출"
echo ""
echo "=========================================="
echo "권장: AIDER 또는 OpenAI API 직접 호출"
echo "=========================================="

# AIDER 설치 확인
if command -v aider &> /dev/null; then
    echo "✓ AIDER 설치됨: $(aider --version)"
else
    echo "⚠ AIDER 미설치"
    echo "  설치: pip install aider-chat"
fi

# Fabric 설치 확인
if command -v fabric &> /dev/null; then
    echo "✓ Fabric 설치됨"
else
    echo "⚠ Fabric 미설치"
    echo "  설치: pip install fabric-ai"
fi

echo ""
echo "설치 완료!"

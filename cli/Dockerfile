# ============================================
# C2JAVA CLI Service Dockerfile
# AIDER, Fabric 통합 CLI 서비스
# ============================================

FROM python:3.11-slim

# 환경변수 설정
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# 시스템 의존성 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    curl \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 작업 디렉토리 설정
WORKDIR /app

# Python 의존성 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# AIDER 설치
RUN pip install --no-cache-dir aider-chat

# Fabric 설치
RUN pip install --no-cache-dir fabric-ai

# 스크립트 복사
COPY . .

# 실행 권한 설정
RUN chmod +x /app/scripts/*.sh 2>/dev/null || true

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import aider; print('healthy')" || exit 1

# 기본 명령어
CMD ["python", "-m", "cli_service"]

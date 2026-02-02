# C2JAVA 설치 가이드

## 필수 요구사항

### 버전 정보
| 구성요소 | 버전 | 비고 |
|---------|------|------|
| Java | 21 LTS (Temurin/OpenJDK) | Spring Boot 3.2.5 호환 |
| Python | 3.11+ | CLI 서비스, MCP 서버 |
| Node.js | 20 LTS | 프론트엔드 빌드 |
| Docker | 24.0+ | 컨테이너 런타임 |
| Docker Compose | 2.20+ | 멀티 컨테이너 관리 |
| Gradle | 8.5+ | 백엔드 빌드 (Wrapper 포함) |

---

## 설치 방법

### 방법 1: Docker만 사용 (권장)

로컬에 Java, Python, Node.js를 설치하지 않고 Docker만으로 실행합니다.

```bash
# 1. Docker Desktop 설치 (macOS)
brew install --cask docker

# 2. Docker Desktop 실행
open /Applications/Docker.app

# 3. 개발 환경 시작
make docker-dev
# 또는
./scripts/docker-dev.sh
```

### 방법 2: 로컬 개발 환경

모든 의존성을 로컬에 설치합니다.

```bash
# 1. 의존성 설치 (macOS)
make install
# 또는
./scripts/install-dependencies.sh

# 2. 터미널 재시작 또는 환경변수 로드
source ~/.zshrc

# 3. 프로젝트 설정
make setup
# 또는
./scripts/setup-project.sh

# 4. 개발 서버 시작
make dev
# 또는
./scripts/start-dev.sh
```

---

## macOS 의존성 수동 설치

Homebrew를 사용하여 수동으로 설치할 수 있습니다.

```bash
# Homebrew 설치 (없는 경우)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Java 21 설치
brew install openjdk@21

# Java 환경변수 설정 (Apple Silicon Mac)
echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@21"' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc

# Python 3.11 설치
brew install python@3.11

# Node.js 20 설치
brew install node@20
echo 'export PATH="/opt/homebrew/opt/node@20/bin:$PATH"' >> ~/.zshrc

# Gradle 설치
brew install gradle

# Docker Desktop 설치
brew install --cask docker

# 환경변수 적용
source ~/.zshrc
```

---

## Linux (Ubuntu/Debian) 의존성 설치

```bash
# 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# Java 21 설치
sudo apt install -y openjdk-21-jdk

# Python 3.11 설치
sudo apt install -y python3.11 python3.11-venv python3-pip

# Node.js 20 설치
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# Docker Compose 설치
sudo apt install -y docker-compose-plugin

# Gradle 설치
sudo apt install -y gradle

# 로그아웃 후 다시 로그인 (Docker 권한 적용)
```

---

## 환경변수 설정

```bash
# 환경변수 파일 생성
cp config/env/.env.example config/env/.env

# 환경변수 편집
vim config/env/.env
```

### 필수 환경변수

```env
# LLM 설정
ACTIVE_LLM_PROVIDER=qwen3
QWEN3_API_URL=http://내부LLM서버:8080/v1
QWEN3_API_KEY=your-api-key

# 데이터베이스
DB_PASSWORD=secure-password

# Airflow
AIRFLOW_DB_PASSWORD=airflow-password
AIRFLOW_FERNET_KEY=your-fernet-key
AIRFLOW_ADMIN_PASSWORD=admin-password

# Grafana
GRAFANA_ADMIN_PASSWORD=grafana-password
```

---

## Docker 이미지 빌드

```bash
# 이미지 빌드
make docker-build
# 또는
./scripts/build-docker.sh latest

# 이미지 빌드 및 tar 파일 저장 (폐쇄망 배포용)
make docker-build-save
# 또는
./scripts/build-docker.sh latest "" true
```

---

## 폐쇄망 배포

### 외부망에서 준비

```bash
# 1. Docker 이미지 빌드 및 저장
./scripts/build-docker.sh 1.0.0 "" true

# 2. 생성된 파일 확인
ls -la c2java-images-1.0.0.tar

# 3. 프로젝트 파일 압축
tar -cvf c2java-project.tar \
    config/ \
    docker/ \
    airflow/ \
    monitoring/ \
    scripts/
```

### 내부망에서 배포

```bash
# 1. 파일 전송 (USB, 보안파일전송 등)

# 2. 압축 해제
tar -xvf c2java-project.tar

# 3. 환경변수 설정
cp config/env/.env.internal config/env/.env
vim config/env/.env  # 내부망 설정으로 수정

# 4. 배포 실행
./scripts/deploy-internal.sh c2java-images-1.0.0.tar 1.0.0
```

---

## 문제 해결

### Java를 찾을 수 없음
```bash
# JAVA_HOME 확인
echo $JAVA_HOME

# Java 경로 직접 지정
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "/opt/homebrew/opt/openjdk@21")
```

### Docker 권한 오류
```bash
# Docker 그룹에 사용자 추가 (Linux)
sudo usermod -aG docker $USER
# 로그아웃 후 다시 로그인
```

### 포트 충돌
```bash
# 사용 중인 포트 확인
lsof -i :8080
lsof -i :3000

# 환경변수로 포트 변경
export SERVER_PORT=8081
export FRONTEND_PORT=3001
```

---

## 접속 URL

| 서비스 | URL | 기본 인증정보 |
|--------|-----|--------------|
| 프론트엔드 | http://localhost:3000 | - |
| 백엔드 API | http://localhost:8080/api | - |
| Swagger UI | http://localhost:8080/api/swagger-ui.html | - |
| Airflow | http://localhost:8081 | admin / (설정값) |
| Grafana | http://localhost:3001 | admin / (설정값) |

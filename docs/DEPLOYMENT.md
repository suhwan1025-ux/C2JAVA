# C2JAVA 배포 가이드

## 배포 아키텍처

### 단일 서버 배포
```
┌─────────────────────────────────────────┐
│              단일 서버                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │   Web   │ │   API   │ │  CLI    │   │
│  │ (3000)  │ │ (8080)  │ │ (8083)  │   │
│  └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │   MCP   │ │Postgres │ │  Redis  │   │
│  │ (8082)  │ │ (5432)  │ │ (6379)  │   │
│  └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────────────┘
```

### 분산 서버 배포 (권장)
```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Web 서버   │    │   API 서버   │    │   CLI 서버   │
│              │───▶│              │───▶│              │
│  Frontend    │    │  Backend     │    │  AIDER       │
│  Nginx       │    │  Spring Boot │    │  Fabric      │
│  (3000, 80)  │    │  (8080)      │    │  (8083)      │
└──────────────┘    └──────────────┘    └──────────────┘
                           │                    │
                           ▼                    ▼
                    ┌──────────────┐    ┌──────────────┐
                    │   DB 서버    │    │  LLM 서버    │
                    │              │    │  (사내망)    │
                    │  PostgreSQL  │    │  QWEN3 VL    │
                    │  Redis       │    │  GPT OSS     │
                    │  (5432,6379) │    │  (8080)      │
                    └──────────────┘    └──────────────┘
```

---

## 배포 유형별 가이드

### 1. 단일 서버 배포 (개발/소규모)

```bash
# 1. 이미지 빌드
./scripts/build-docker.sh 1.0.0 "" true

# 2. 환경변수 설정
cp config/env/.env.example config/env/.env
vim config/env/.env

# 3. 배포
./scripts/deploy-internal.sh c2java-images-1.0.0.tar 1.0.0
```

### 2. 분산 서버 배포 (운영/다중사용자)

```bash
# 1. 이미지 빌드 및 저장
./scripts/build-docker.sh 1.0.0 "" true

# 2. 환경변수 설정 (서버 주소 지정)
cp config/env/.env.internal config/env/.env
vim config/env/.env

# 3. 분산 배포
./scripts/deploy-distributed.sh c2java-images-1.0.0.tar 1.0.0
```

---

## 환경변수 설정

### 필수 환경변수

```env
# 데이터베이스
DB_PASSWORD=your-secure-password

# LLM 서버 (사내망)
QWEN3_API_URL=http://llm-server:8080/v1
QWEN3_API_KEY=your-api-key

# Airflow
AIRFLOW_FERNET_KEY=$(python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())")
AIRFLOW_ADMIN_PASSWORD=your-admin-password

# Grafana
GRAFANA_ADMIN_PASSWORD=your-grafana-password
```

### 분산 배포시 추가 설정

```env
# CLI 서비스가 별도 서버에 있는 경우
CLI_SERVICE_URL=http://192.168.1.100:8083

# MCP 서버가 별도 서버에 있는 경우
MCP_SERVICE_URL=http://192.168.1.101:8082

# 동시 작업 처리 수
CLI_WORKER_COUNT=5
CONVERSION_MAX_CONCURRENT=10
```

---

## 배포 후 설정 변경

### 방법 1: 관리자 웹 페이지 (권장)

1. 웹 UI 접속: `http://서버주소:3000`
2. 관리자 계정으로 로그인
3. "환경설정 관리" 메뉴 접속
4. 원하는 설정 변경
5. 변경 즉시 반영 (재시작 불필요)

### 방법 2: 환경변수 파일 수정

```bash
# 환경변수 파일 수정
vim config/env/.env

# 서비스 재시작
docker compose -f docker/compose/docker-compose.distributed.yml restart backend cli-service mcp-server
```

### 방법 3: API 직접 호출

```bash
# 설정 조회
curl http://localhost:8080/api/v1/configs

# 설정 변경
curl -X PUT http://localhost:8080/api/v1/configs/llm.active_provider \
  -H "Content-Type: application/json" \
  -d '{"value": "gpt_oss"}'

# 여러 설정 일괄 변경
curl -X PUT http://localhost:8080/api/v1/configs/batch \
  -H "Content-Type: application/json" \
  -d '{
    "llm.qwen3.api_url": "http://new-llm-server:8080/v1",
    "cli.aider.enabled": "true"
  }'
```

---

## 서버별 배포 (분리 배포)

### Web 서버 (프론트엔드)

```bash
# 프론트엔드만 배포
docker run -d \
  --name c2java-web \
  -p 3000:80 \
  c2java-frontend:1.0.0
```

### API 서버 (백엔드)

```bash
# 백엔드만 배포
docker run -d \
  --name c2java-backend \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db-server:5432/c2java \
  -e SPRING_DATASOURCE_PASSWORD=your-password \
  -e CLI_SERVICE_URL=http://cli-server:8083 \
  -e MCP_SERVICE_URL=http://mcp-server:8082 \
  c2java-backend:1.0.0
```

### CLI 서버 (AIDER, Fabric)

```bash
# CLI 서비스만 배포
docker run -d \
  --name c2java-cli \
  -p 8083:8083 \
  -e QWEN3_API_URL=http://llm-server:8080/v1 \
  -e QWEN3_API_KEY=your-api-key \
  -e CLI_WORKER_COUNT=5 \
  c2java-cli:1.0.0
```

### MCP 서버

```bash
# MCP 서버만 배포
docker run -d \
  --name c2java-mcp \
  -p 8082:8082 \
  c2java-mcp:1.0.0
```

---

## 멀티유저 설정

### 동시 사용자 지원 설정

```env
# CLI 서비스 워커 수 (동시 변환 작업)
CLI_WORKER_COUNT=5

# 백엔드 최대 동시 작업
CONVERSION_MAX_CONCURRENT=10

# Redis 연결 (세션/캐시)
SPRING_REDIS_HOST=redis-server
SPRING_REDIS_PORT=6379
```

### 사용자 계정 관리

- 기본 관리자: `admin` / `admin123`
- 최초 로그인 후 비밀번호 변경 필수
- 관리자 페이지에서 사용자 추가/관리

---

## 트러블슈팅

### 서비스 연결 실패

```bash
# 네트워크 확인
docker network ls
docker network inspect c2java_c2java-backend

# 서비스 로그 확인
docker compose -f docker/compose/docker-compose.distributed.yml logs backend
docker compose -f docker/compose/docker-compose.distributed.yml logs cli-service
```

### LLM 연결 실패

1. 관리자 페이지에서 LLM URL 확인
2. 네트워크 연결 테스트:
   ```bash
   curl http://llm-server:8080/v1/models
   ```
3. API Key 확인

### 설정 변경이 반영되지 않음

```bash
# 설정 재로드
curl -X POST http://localhost:8080/api/v1/configs/reload

# 또는 서비스 재시작
docker compose -f docker/compose/docker-compose.distributed.yml restart backend
```

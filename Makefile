# ============================================
# C2JAVA Makefile
# 프로젝트 빌드 및 관리
# ============================================

.PHONY: help install dev build docker-build docker-dev docker-prod clean

VERSION ?= latest

help: ## 도움말 표시
	@echo "C2JAVA 프로젝트 명령어"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ----------------------------------------
# 로컬 개발 환경
# ----------------------------------------

install: ## 의존성 설치 (macOS)
	@chmod +x scripts/*.sh
	@./scripts/install-dependencies.sh

setup: ## 프로젝트 설정
	@./scripts/setup-project.sh

dev: ## 로컬 개발 서버 시작
	@./scripts/start-dev.sh

# ----------------------------------------
# Docker 환경
# ----------------------------------------

docker-dev: ## Docker 개발 환경 시작
	@chmod +x scripts/*.sh
	@./scripts/docker-dev.sh

docker-build: ## Docker 이미지 빌드
	@./scripts/build-docker.sh $(VERSION)

docker-build-save: ## Docker 이미지 빌드 및 tar 저장
	@./scripts/build-docker.sh $(VERSION) "" true

docker-prod: ## Docker 운영 환경 시작
	@docker compose -f docker/compose/docker-compose.prod.yml up -d

docker-stop: ## Docker 환경 중지
	@docker compose -f docker/compose/docker-compose.dev.yml down 2>/dev/null || true
	@docker compose -f docker/compose/docker-compose.prod.yml down 2>/dev/null || true

docker-logs: ## Docker 로그 보기
	@docker compose -f docker/compose/docker-compose.dev.yml logs -f 2>/dev/null || \
	 docker compose -f docker/compose/docker-compose.prod.yml logs -f

# ----------------------------------------
# 배포
# ----------------------------------------

deploy: ## 단일 서버 배포 (tar 파일 필요)
	@./scripts/deploy-internal.sh c2java-images-$(VERSION).tar $(VERSION)

deploy-distributed: ## 분산 서버 배포 (다중사용자, CLI/MCP 분리)
	@./scripts/deploy-distributed.sh c2java-images-$(VERSION).tar $(VERSION)

# ----------------------------------------
# 백엔드
# ----------------------------------------

backend-build: ## 백엔드 빌드
	@cd backend/c2java-api && ./gradlew build -x test

backend-test: ## 백엔드 테스트
	@cd backend/c2java-api && ./gradlew test

backend-run: ## 백엔드 실행
	@cd backend/c2java-api && ./gradlew bootRun

# ----------------------------------------
# 프론트엔드
# ----------------------------------------

frontend-install: ## 프론트엔드 의존성 설치
	@cd frontend/c2java-web && npm install

frontend-build: ## 프론트엔드 빌드
	@cd frontend/c2java-web && npm run build

frontend-dev: ## 프론트엔드 개발 서버
	@cd frontend/c2java-web && npm run dev

# ----------------------------------------
# 정리
# ----------------------------------------

clean: ## 빌드 산출물 정리
	@echo "빌드 산출물 정리 중..."
	@rm -rf backend/c2java-api/build
	@rm -rf frontend/c2java-web/dist
	@rm -rf frontend/c2java-web/node_modules
	@rm -rf cli/venv
	@rm -rf mcp/venv
	@rm -f *.tar
	@echo "완료"

clean-docker: ## Docker 리소스 정리
	@docker compose -f docker/compose/docker-compose.dev.yml down -v 2>/dev/null || true
	@docker compose -f docker/compose/docker-compose.prod.yml down -v 2>/dev/null || true
	@docker system prune -f

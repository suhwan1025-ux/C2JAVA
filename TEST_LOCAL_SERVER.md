# 로컬 서버 관리 테스트 가이드

## 문제 해결 완료

**발견된 문제:**
- `docker-compose.yml` 파일 경로가 잘못되어 있었습니다
- 원래 코드: `/Users/dongsoo/Desktop/C2JAVA/airflow`
- 실제 경로: `/Users/dongsoo/Desktop/C2JAVA/docker/compose`

**수정 완료:**
- `LocalServerService.java`의 `startAirflow()` 메서드 경로 수정
- `LocalServerService.java`의 `stopAirflow()` 메서드 경로 수정

## 테스트 방법

### 1. 백엔드 재시작
```bash
# 현재 실행 중인 백엔드 중지
pkill -f "c2java.C2JavaApplication"

# IDE (IntelliJ/Cursor)에서 재시작
# 또는 터미널에서:
cd /Users/dongsoo/Desktop/C2JAVA/backend/c2java-api
./gradlew bootRun
```

### 2. API 직접 테스트

**서비스 상태 확인:**
```bash
curl http://localhost:8080/api/v1/admin/local-servers/status | jq .
```

**Airflow 중지 테스트:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/local-servers/airflow/stop | jq .
```

**Airflow 시작 테스트:**
```bash
curl -X POST http://localhost:8080/api/v1/admin/local-servers/airflow/start | jq .
```

**로그 확인:**
```bash
curl http://localhost:8080/api/v1/admin/local-servers/airflow/logs?lines=50 | jq .
```

### 3. 웹 UI 테스트

1. 웹 브라우저에서 `http://localhost:5173/admin` 접속
2. "로컬 서버" 탭 클릭
3. Airflow 카드에서:
   - 현재 상태 확인 (Running/Stopped)
   - "중지" 버튼 클릭 → 상태 변경 확인
   - "시작" 버튼 클릭 → 상태 변경 확인
   - "로그" 버튼 클릭 → 로그 뷰어 확인

### 4. 수동 확인

**Airflow 컨테이너 상태:**
```bash
docker ps | grep airflow
```

**CLI Service 프로세스:**
```bash
pgrep -f cli_service.py
```

## 현재 상태

- ✅ `LocalServerService.java` 경로 수정 완료
- ✅ `AdminController.java` API 엔드포인트 추가 완료
- ✅ `Admin.tsx` UI 구현 완료
- ✅ `api.ts` API 클라이언트 추가 완료
- ⏳ **백엔드 재시작 필요**

## 예상 동작

### Airflow
- **시작**: `docker-compose -f docker-compose.yml up -d`
- **중지**: `docker-compose -f docker-compose.yml down`
- **상태**: Docker 컨테이너 확인

### CLI Service
- **시작**: `python3 cli_service.py` (백그라운드)
- **중지**: `pkill -f cli_service.py`
- **상태**: 프로세스 PID 확인

## 다음 단계

1. IDE에서 백엔드를 재시작하세요
2. 웹 브라우저에서 관리자 페이지 → 로컬 서버 탭 접속
3. 서비스 시작/중지 버튼을 테스트하세요
4. 로그 뷰어가 정상 작동하는지 확인하세요

## 추가 개선 사항 (선택)

- [ ] docker-compose 경로를 환경변수로 설정
- [ ] CLI Service 가상환경 자동 감지
- [ ] 서비스 헬스 체크 추가
- [ ] 로그 필터링 기능
- [ ] 로그 다운로드 기능

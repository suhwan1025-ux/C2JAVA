# C2JAVA 변환 파이프라인

## 전체 아키텍처

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        C2JAVA 변환 파이프라인                              │
└──────────────────────────────────────────────────────────────────────────┘

사용자 업로드
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 1단계: 파일 분석 (CFileAnalyzerService)                                  │
├─────────────────────────────────────────────────────────────────────────┤
│  - C 파일 파싱 (함수, 구조체, SQL, #define 추출)                         │
│  - 파일 타입 분류 (.c/.h/.pc)                                            │
│  - 분석 결과 DB 저장 (analysis_results)                                  │
│  - 통계: 함수 수, 구조체 수, SQL 수                                       │
└─────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2단계: Airflow DAG 생성 (AirflowDagService)                              │
├─────────────────────────────────────────────────────────────────────────┤
│  - 작업별 Python DAG 파일 생성                                            │
│  - 5개 Task 정의: analyze → convert → compile → test → finalize         │
│  - DAG 파일 저장: /airflow/dags/c2java_{job_id}.py                      │
└─────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3단계: Airflow DAG 트리거 (AirflowApiService)                            │
├─────────────────────────────────────────────────────────────────────────┤
│  - Airflow REST API 호출                                                 │
│  - DAG 실행 시작                                                         │
│  - dag_run_id 반환 및 저장                                               │
└─────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Airflow Task 실행 (워커 서버)                                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Task 1: analyze_c_files                                                │
│    ↓ POST /v1/conversions/{jobId}/analyze                               │
│    → CFileAnalyzerService.analyzeFile()                                 │
│    → 분석 결과 DB 저장                                                   │
│                                                                          │
│  Task 2: convert_to_java                                                │
│    ↓ POST /v1/conversions/{jobId}/convert                               │
│    → RulesService.getLanguageDetail() - 규칙 로드                        │
│    → LlmService.convertCode() - LLM API 호출                            │
│    → CodeConverterService.convertCFiles() - Java 파일 생성              │
│    → 파일 저장: /app/output/{jobId}/                                    │
│                                                                          │
│  Task 3: compile_java                                                   │
│    ↓ POST /v1/conversions/{jobId}/compile                               │
│    → GradleBuildService.buildProject()                                  │
│    → ./gradlew clean build 실행                                         │
│    → 컴파일 에러 수집 및 DB 저장                                         │
│                                                                          │
│  Task 4: run_tests                                                      │
│    ↓ POST /v1/conversions/{jobId}/test                                  │
│    → TestRunnerService.runTests()                                       │
│    → ./gradlew test 실행                                                │
│    → 테스트 결과 DB 저장                                                 │
│                                                                          │
│  Task 5: finalize                                                       │
│    ↓ PUT /v1/conversions/{jobId}/status                                 │
│    → 작업 완료 처리                                                      │
│    → progress = 100, status = COMPLETED                                 │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 사용자 모니터링 (JobMonitor.tsx)                                         │
├─────────────────────────────────────────────────────────────────────────┤
│  - 3초마다 자동 갱신                                                     │
│  - GET /v1/conversions/{jobId}/status/detailed                          │
│  - 진행률 바, 단계별 상태, Airflow Task 상태                             │
│  - 분석/검증 결과 통계 표시                                              │
└─────────────────────────────────────────────────────────────────────────┘
```

## 핵심 컴포넌트

### 백엔드 서비스

| 서비스 | 역할 | 주요 메서드 |
|--------|------|------------|
| **CFileAnalyzerService** | C 파일 구조 분석 | `analyzeFile()` |
| **RulesService** | 언어별 변환 규칙 관리 | `getLanguageDetail()` |
| **LlmService** | LLM API 호출 | `convertCode()` |
| **CodeConverterService** | 변환 오케스트레이션 | `convertCFiles()` |
| **AirflowDagService** | DAG 파일 생성 | `createConversionDag()` |
| **AirflowApiService** | Airflow REST API | `triggerDag()`, `getDagRunStatus()` |
| **GradleBuildService** | 컴파일 검증 | `buildProject()` |
| **TestRunnerService** | 테스트 실행 | `runTests()` |
| **ConversionPipelineService** | 전체 파이프라인 관리 | `startConversionWithAirflow()` |

### 데이터베이스

**conversion_jobs 테이블**
- 변환 작업 메타데이터
- 진행 상태, 단계, 진행률
- Airflow DAG/Run ID
- 분석/검증 결과

**analysis_results 테이블**
- 파일별 분석 결과
- 함수, 구조체, SQL 정보 (JSON)

### 프론트엔드 페이지

| 페이지 | 경로 | 기능 |
|--------|------|------|
| **Upload.tsx** | `/upload` | C 파일 업로드, 대상 언어 선택 |
| **Jobs.tsx** | `/jobs` | 전체 작업 목록, 진행률 표시 |
| **JobMonitor.tsx** | `/jobs/:id/monitor` | 실시간 모니터링, Airflow 상태 |
| **Admin.tsx** | `/admin` | 변환 규칙 관리, 환경 설정 |

## 변환 규칙

### conversion-rules.yaml (136줄, 3.8KB)
- 타입 매핑 (int, char*, struct 등)
- 함수 매핑 (printf, malloc, strcmp 등)
- SQL 보존 규칙
- 변환 불가 항목 처리

### project-structure.yaml (138줄, 4KB)
- 파일 생성 규칙 (.pc → Repository, .c → Service)
- 디렉토리 구조
- 클래스 템플릿
- 매핑 파일 형식

## 사용 흐름

### 1. 관리자: 변환 규칙 설정
```
관리자 로그인 → 변환 규칙 탭 → 언어 생성 (예: springboot-3.2.5)
→ 변환 규칙 업로드 → 프로젝트 구조 업로드
```

### 2. 사용자: 파일 업로드
```
파일 업로드 페이지 → 대상 언어 선택 (springboot-3.2.5)
→ C 파일 업로드 → 변환 시작
```

### 3. 자동 변환 파이프라인
```
분석 (10%) → 변환 (30-60%) → 컴파일 (65-80%) → 테스트 (85-95%) → 완료 (100%)
```

### 4. 실시간 모니터링
```
작업 목록 → 모니터링 버튼 → 실시간 진행률 확인
→ Airflow Task 상태 확인 → 결과 다운로드
```

## 주요 기능

### ✓ 구현 완료
- [x] C 파일 구조 분석 (함수, 구조체, SQL 추출)
- [x] 언어별 변환 규칙 관리
- [x] LLM API 연동 (QWEN3, GPT OSS)
- [x] Airflow DAG 자동 생성
- [x] Airflow REST API 연동
- [x] 컴파일 검증 (Gradle)
- [x] 런타임 테스트 (JUnit)
- [x] 실시간 진행률 모니터링
- [x] PostgreSQL 작업 이력 저장
- [x] 파일 매핑 추적성 (conversion-mapping.json)

### 설정 필요
- [ ] Airflow 서버 설치 및 설정
- [ ] LLM 서버 연결 (QWEN3 또는 GPT OSS)
- [ ] 워커 서버 환경변수 설정

## 환경 변수

```bash
# Airflow (워커 서버)
AIRFLOW_ENABLED=true
AIRFLOW_URL=http://워커서버:8081
AIRFLOW_USERNAME=admin
AIRFLOW_PASSWORD=admin

# LLM
ACTIVE_LLM_PROVIDER=qwen3
QWEN3_API_URL=http://llm-server:8080/v1
QWEN3_API_KEY=your-key

# 변환 디렉토리
CONVERSION_WORKSPACE=/app/workspace
OUTPUT_DIR=/app/output
RULES_DIR=/app/config/rules
```

## 다음 단계

1. **Airflow 서버 구축**
   - Docker Compose로 Airflow 설치
   - PostgreSQL 메타스토어 연결
   - DAGs 폴더 마운트

2. **LLM 서버 연결**
   - QWEN3 또는 GPT OSS 서버 설정
   - API 키 발급
   - 연결 테스트

3. **통합 테스트**
   - 샘플 C 파일로 전체 파이프라인 테스트
   - 각 단계별 로그 확인
   - 에러 핸들링 검증

4. **프로덕션 배포**
   - Docker Compose 통합
   - 환경 변수 설정
   - 모니터링 대시보드 확인

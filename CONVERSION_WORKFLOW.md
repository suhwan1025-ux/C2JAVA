# C2JAVA 변환 작업 워크플로우

## 📋 전체 프로세스

### 1️⃣ 환경설정 및 서비스 상태 확인 ✅
**위치**: `Upload.tsx` - 파일 업로드 페이지

**동작**:
- **폐쇄망 (Closed Network)**:
  - 워커 서버 URL 확인 (`WORKER_SERVER_URL`)
  - 워커 서버의 CLI Service 상태 확인 (`/health` endpoint)
  - Aider CLI 또는 Fabric CLI 가동 여부 확인

- **외부망 (External Network)**:
  - 로컬 Airflow 상태 확인 (Docker 컨테이너)
  - 로컬 CLI Service 상태 확인 (Python 프로세스)
  - Cursor CLI 또는 Claude CLI 설정 확인

**체크 항목**:
```typescript
// 폐쇄망
- workerServerStatus.enabled === true
- workerServerStatus.cliService.running === true

// 외부망
- localServerStatus.airflow.running === true
- localServerStatus['cli-service'].running === true
```

**결과**: 모든 서비스가 준비되지 않으면 **파일 업로드 버튼 비활성화**

---

### 2️⃣ 파일 업로드 ✅
**위치**: `ConversionService.createConversionJob()`

**동작**:
- 사용자가 선택한 C/C++ 파일 업로드
- 파일 저장 위치:
  - **폐쇄망**: 워커 서버의 공유 스토리지
  - **외부망**: 로컬 워크스페이스 `{WORKSPACE_PATH}/{userId}/{projectName}/`
- `ConversionJob` 엔티티 생성 (DB 저장)
- 작업 상태: `PENDING`

**코드**:
```java
// 파일 저장
for (MultipartFile file : files) {
    String savedPath = fileStorageService.uploadFile(file, jobId, userId, projectName);
    savedPaths.add(Paths.get(savedPath));
}

// 작업 생성
ConversionJob job = ConversionJob.builder()
    .jobId(UUID.randomUUID())
    .userId(currentUserId)
    .status(JobStatus.PENDING)
    .build();
```

---

### 3️⃣ Airflow 파이프라인 시작 ✅
**위치**: `ConversionPipelineService.startConversionWithAirflow()`

**동작**:
- 동적 Airflow DAG 생성
- DAG 파일 이름: `c2java_{jobId}.py`
- DAG 트리거 (REST API 호출)
- 작업 상태: `PENDING` → 진행률 5%

**배치 처리**:
- 파일 개수가 많을 경우 배치로 분할
- 배치 크기: `CONVERSION_BATCH_SIZE` (기본 10개)
- 최대 병렬 배치: `CONVERSION_MAX_PARALLEL` (기본 3개)

---

### 4️⃣ 파일 구조 분석 (ANALYZE) ✅
**위치**: 
- Airflow Task: `t1_analyze`
- Backend: `ConversionPipelineService.analyzeStep()`

**동작**:
- 각 C/C++ 파일 파싱 및 분석
- AST(Abstract Syntax Tree) 생성
- 추출 정보:
  - 함수 목록 및 시그니처
  - 구조체 정의
  - Enum 타입
  - SQL 쿼리 (Pro*C)
  - Include 파일
  - Define 매크로
  - 전역 변수
- 분석 결과 DB 저장 (`AnalysisResult` 테이블)
- 작업 상태: `ANALYZING` → 진행률 10-25%

**코드**:
```java
CFileStructure structure = analyzerService.analyzeFile(filePath);
AnalysisResult analysis = AnalysisResult.builder()
    .jobId(jobId)
    .functions(toJson(structure.getFunctions()))
    .structs(toJson(structure.getStructs()))
    .build();
analysisRepository.save(analysis);
```

---

### 5️⃣ 변환 규칙 참조 및 코드 변환 (CONVERT) ✅
**위치**: 
- Airflow Task: `t2_convert` (또는 `t2_convert_batch_N`)
- Backend: `ConversionPipelineService.convertStep()`

**동작**:
1. **변환 규칙 로드**:
   - 규칙 파일: `config/rules/{targetLanguage}/conversion-rules.yaml`
   - 프로젝트 구조: `config/rules/{targetLanguage}/project-structure.yaml`
   - 템플릿 파일: `*.template`

2. **LLM/CLI 도구 선택**:
   - **폐쇄망**: Aider CLI + 내부 LLM (Qwen3/GPT OSS)
   - **외부망**: Cursor CLI / Claude CLI

3. **Java 코드 생성**:
   - 함수 → Java 메서드
   - 구조체 → Java 클래스
   - Pro*C SQL → JPA/MyBatis
   - 포인터 → 참조 타입
   - 메모리 관리 → 자동 GC

4. **생성 파일 저장**:
   - 출력 경로: `/tmp/c2java/output/{jobId}/`
   - Spring Boot 프로젝트 구조 생성
   - Gradle 빌드 파일 생성

- 작업 상태: `CONVERTING` → 진행률 30-60%

**변환 규칙 예시**:
```yaml
type_mappings:
  - c_type: "char*"
    java_type: "String"
  - c_type: "int*"
    java_type: "Integer[]"

function_patterns:
  - pattern: "main"
    template: "springBootMain"
  - pattern: "*_insert"
    template: "jpaRepository.save"
```

---

### 6️⃣ 컴파일 테스트 (COMPILE) ✅
**위치**: 
- Airflow Task: `t3_compile`
- Backend: `ConversionPipelineService.compileStep()`

**동작**:
- Gradle 빌드 실행: `gradle build`
- 컴파일 오류 검사
- 자동 수정 시도 (최대 3회):
  1. LLM/CLI에 오류 메시지 전달
  2. 수정된 코드 재생성
  3. 재컴파일
- 컴파일 결과 DB 저장 (`CompileResult` 테이블)
- 작업 상태: `COMPILING` → 진행률 60-80%

**코드**:
```java
GradleBuildResult buildResult = buildService.buildProject(outputPath);
if (!buildResult.isSuccess()) {
    // 자동 수정 시도
    for (int attempt = 1; attempt <= 3; attempt++) {
        String fixedCode = llmService.fixCompileError(code, buildResult.getErrors());
        buildResult = buildService.buildProject(outputPath);
        if (buildResult.isSuccess()) break;
    }
}
```

---

### 7️⃣ 런타임 테스트 (TEST) ✅
**위치**: 
- Airflow Task: `t4_test`
- Backend: `ConversionPipelineService.testStep()`

**동작**:
- 생성된 Java 애플리케이션 실행
- 단위 테스트 실행 (JUnit)
- 기능 테스트 실행:
  - REST API 엔드포인트 테스트
  - DB 연결 테스트
  - 비즈니스 로직 테스트
- 테스트 결과 분석
- 테스트 결과 DB 저장 (`RuntimeResult` 테이블)
- 작업 상태: `TESTING` → 진행률 80-95%

**테스트 항목**:
```java
// REST API 테스트
@Test
void testGeneratedEndpoint() {
    ResponseEntity<?> response = restTemplate.getForEntity("/api/...", ...);
    assertEquals(HttpStatus.OK, response.getStatusCode());
}

// DB 테스트
@Test
void testDatabaseConnection() {
    assertTrue(dataSource.getConnection().isValid(5));
}
```

---

### 8️⃣ 변환 요약 생성 및 작업 완료 (FINALIZE) ✅
**위치**: 
- Airflow Task: `t5_finalize`
- Backend: `ConversionPipelineService.finalizeJob()`

**동작**:
- 변환 통계 수집:
  - 변환된 파일 수
  - 생성된 Java 클래스 수
  - 함수/메서드 수
  - 컴파일 성공/실패
  - 테스트 성공/실패
  - 총 소요 시간

- 변환 요약 리포트 생성:
  - 변환 내역 상세
  - 수동 검토 필요 항목
  - 알려진 이슈 및 제한사항
  - 마이그레이션 가이드

- 작업 상태 업데이트:
  - `COMPLETED` (성공) 또는 `FAILED` (실패)
  - 진행률 100%
  - `completedAt` 타임스탬프 설정

**요약 리포트 예시**:
```json
{
  "jobId": "...",
  "status": "COMPLETED",
  "statistics": {
    "sourceFiles": 45,
    "generatedFiles": 67,
    "functions": 234,
    "structs": 28,
    "compileSuccess": true,
    "testsPassed": 42,
    "testsFailed": 3,
    "duration": "00:12:34"
  },
  "reviewRequired": [
    {
      "file": "UserService.java",
      "line": 45,
      "reason": "복잡한 포인터 연산 변환",
      "priority": "HIGH"
    }
  ]
}
```

---

## 🔄 Airflow DAG 구조

```python
# 동적으로 생성되는 DAG 구조
dag = DAG('c2java_{jobId}', ...)

# 단일 배치 (파일 수 < 10)
t1_analyze >> t2_convert >> t3_compile >> t4_test >> t5_finalize

# 다중 배치 (파일 수 >= 10)
t1_analyze >> [t2_convert_batch_0, t2_convert_batch_1, ...] >> t3_compile >> t4_test >> t5_finalize
```

---

## 🎯 사용자 제시 프로세스 검증

| 단계 | 사용자 제시 | 현재 구현 | 상태 |
|------|------------|----------|------|
| 1 | 환경설정 프리셋 확인 후 관련 서비스 기동여부 확인 | ✅ Upload.tsx - 서비스 상태 체크 | ✅ 일치 |
| 2 | 파일 업로드 | ✅ ConversionService.createConversionJob() | ✅ 일치 |
| 3 | 파일 구조 분석 | ✅ ConversionPipelineService.analyzeStep() | ✅ 일치 |
| 4 | 변환 규칙 참조 | ✅ CodeConverterService - 규칙 로드 및 적용 | ✅ 일치 |
| 5 | 변환 시작 | ✅ ConversionPipelineService.convertStep() | ✅ 일치 |
| 6 | 컴파일 테스트 | ✅ ConversionPipelineService.compileStep() | ✅ 일치 |
| 7 | 런타임 테스트 | ✅ ConversionPipelineService.testStep() | ✅ 일치 |
| 8 | 변환 요약 생성 | ✅ ConversionPipelineService.finalizeJob() | ✅ 일치 |

## ✅ 결론

**사용자가 제시한 프로세스는 현재 구현과 100% 일치합니다!**

모든 단계가 정확하게 구현되어 있으며, 추가로:
- **취소 기능**: 각 단계에서 작업 취소 상태 확인
- **자동 재시도**: 컴파일 오류 자동 수정 (최대 3회)
- **배치 처리**: 대량 파일 처리를 위한 병렬 변환
- **실시간 로그**: 각 단계별 진행 상황 로그 기록
- **에러 핸들링**: 단계별 실패 처리 및 롤백

---

## 📊 작업 상태 흐름

```
PENDING (업로드 완료)
  ↓
ANALYZING (파일 분석 중) - 10-25%
  ↓
CONVERTING (코드 변환 중) - 30-60%
  ↓
COMPILING (컴파일 중) - 60-80%
  ↓
TESTING (테스트 중) - 80-95%
  ↓
COMPLETED (완료) - 100%
```

## 🚫 예외 상태

- **CANCELLED**: 사용자가 작업 취소
- **FAILED**: 단계 실패 (재시도 후에도 실패)

---

**마지막 업데이트**: 2026-02-03
**문서 버전**: 1.0

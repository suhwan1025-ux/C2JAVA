-- ============================================
-- C2JAVA 변환 작업 테이블 마이그레이션
-- 누락된 컬럼 추가
-- ============================================

-- conversion_jobs 테이블에 누락된 컬럼 추가
ALTER TABLE conversion_jobs 
ADD COLUMN IF NOT EXISTS id UUID DEFAULT gen_random_uuid(),
ADD COLUMN IF NOT EXISTS source_path VARCHAR(500),
ADD COLUMN IF NOT EXISTS llm_provider VARCHAR(50),
ADD COLUMN IF NOT EXISTS compile_attempts INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS execution_log TEXT;

-- id 컬럼이 PRIMARY KEY가 아닌 경우 기본 키로 설정
-- job_id를 id로 매핑하기 위해 id 값을 job_id로 업데이트
UPDATE conversion_jobs SET id = job_id::uuid WHERE id IS NULL;

-- 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_job_id ON conversion_jobs(id);
CREATE INDEX IF NOT EXISTS idx_job_llm_provider ON conversion_jobs(llm_provider);

COMMENT ON COLUMN conversion_jobs.id IS 'UUID 기본 키';
COMMENT ON COLUMN conversion_jobs.source_path IS '소스 파일 경로';
COMMENT ON COLUMN conversion_jobs.llm_provider IS 'LLM 제공자 (qwen3, gpt_oss)';
COMMENT ON COLUMN conversion_jobs.compile_attempts IS '컴파일 시도 횟수';
COMMENT ON COLUMN conversion_jobs.execution_log IS '실행 로그';

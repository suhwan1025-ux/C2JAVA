-- ============================================
-- C2JAVA 변환 작업 스키마
-- ============================================

-- 변환 작업 테이블
CREATE TABLE IF NOT EXISTS conversion_jobs (
    job_id VARCHAR(36) PRIMARY KEY,
    job_name VARCHAR(255) NOT NULL,
    user_id UUID REFERENCES users(id),
    target_language VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    current_stage VARCHAR(20),
    progress INTEGER NOT NULL DEFAULT 0,
    
    -- 원본 파일 정보
    source_file_path VARCHAR(500),
    source_file_count INTEGER,
    
    -- 생성 파일 정보
    generated_file_count INTEGER,
    output_path VARCHAR(500),
    
    -- Airflow 정보
    airflow_dag_id VARCHAR(100),
    airflow_run_id VARCHAR(100),
    
    -- 분석 결과
    function_count INTEGER,
    struct_count INTEGER,
    sql_count INTEGER,
    review_required_count INTEGER,
    
    -- 검증 결과
    compile_success BOOLEAN,
    compile_errors TEXT,
    test_success BOOLEAN,
    test_results TEXT,
    
    -- 에러 정보
    error_message TEXT,
    error_stack_trace TEXT,
    
    -- 타임스탬프
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- 인덱스
    INDEX idx_job_user (user_id),
    INDEX idx_job_status (status),
    INDEX idx_job_created (created_at DESC)
);

-- 분석 결과 테이블
CREATE TABLE IF NOT EXISTS analysis_results (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL REFERENCES conversion_jobs(job_id) ON DELETE CASCADE,
    source_file VARCHAR(255) NOT NULL,
    file_type VARCHAR(20),
    
    -- 분석된 요소들 (JSON)
    functions TEXT,
    structs TEXT,
    enums TEXT,
    sql_queries TEXT,
    includes TEXT,
    defines TEXT,
    global_variables TEXT,
    
    -- 통계
    line_count INTEGER,
    function_count INTEGER,
    struct_count INTEGER,
    complexity_score INTEGER,
    
    -- 인덱스
    INDEX idx_analysis_job (job_id)
);

-- 업데이트 트리거
CREATE OR REPLACE FUNCTION update_conversion_job_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_conversion_job_update
    BEFORE UPDATE ON conversion_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_conversion_job_timestamp();

-- 초기 데이터
COMMENT ON TABLE conversion_jobs IS 'C to Java 변환 작업 정보';
COMMENT ON TABLE analysis_results IS 'C 파일 분석 결과';

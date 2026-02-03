-- ============================================
-- C2JAVA 데이터베이스 초기화 스크립트
-- ============================================

-- Airflow 데이터베이스 생성
CREATE DATABASE airflow;
CREATE USER airflow_user WITH ENCRYPTED PASSWORD 'airflow_password';
GRANT ALL PRIVILEGES ON DATABASE airflow TO airflow_user;

-- Airflow 데이터베이스에 연결하여 스키마 권한 부여
\c airflow;
GRANT ALL ON SCHEMA public TO airflow_user;
GRANT ALL ON ALL TABLES IN SCHEMA public TO airflow_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO airflow_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO airflow_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO airflow_user;

-- C2JAVA 메인 데이터베이스 스키마 생성
\c c2java;

-- 변환 작업 테이블
CREATE TABLE IF NOT EXISTS conversion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    source_file_path TEXT NOT NULL,
    output_file_path TEXT,
    llm_provider VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0
);

-- 변환 로그 테이블
CREATE TABLE IF NOT EXISTS conversion_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES conversion_jobs(id),
    log_level VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 파일 분석 결과 테이블
CREATE TABLE IF NOT EXISTS file_analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES conversion_jobs(id),
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    line_count INTEGER,
    function_count INTEGER,
    struct_count INTEGER,
    include_count INTEGER,
    complexity_score DECIMAL(5,2),
    analysis_result JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 컴파일 결과 테이블
CREATE TABLE IF NOT EXISTS compile_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES conversion_jobs(id),
    attempt_number INTEGER NOT NULL DEFAULT 1,
    success BOOLEAN NOT NULL,
    error_output TEXT,
    warning_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 런타임 테스트 결과 테이블
CREATE TABLE IF NOT EXISTS runtime_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES conversion_jobs(id),
    test_name VARCHAR(255),
    success BOOLEAN NOT NULL,
    execution_time_ms INTEGER,
    error_output TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 리뷰 테이블
CREATE TABLE IF NOT EXISTS conversion_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES conversion_jobs(id),
    review_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    score DECIMAL(3,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 환경 설정 테이블
CREATE TABLE IF NOT EXISTS app_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key VARCHAR(255) UNIQUE NOT NULL,
    setting_value TEXT,
    description TEXT,
    is_sensitive BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- JDBC 설정 테이블
CREATE TABLE IF NOT EXISTS jdbc_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_name VARCHAR(255) NOT NULL,
    jdbc_url TEXT NOT NULL,
    jdbc_user VARCHAR(255),
    jdbc_driver VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_conversion_jobs_status ON conversion_jobs(status);
CREATE INDEX idx_conversion_jobs_created_at ON conversion_jobs(created_at);
CREATE INDEX idx_conversion_logs_job_id ON conversion_logs(job_id);
CREATE INDEX idx_file_analysis_job_id ON file_analysis(job_id);
CREATE INDEX idx_compile_results_job_id ON compile_results(job_id);
CREATE INDEX idx_runtime_results_job_id ON runtime_results(job_id);

-- 업데이트 트리거 함수
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 트리거 적용
CREATE TRIGGER update_conversion_jobs_updated_at
    BEFORE UPDATE ON conversion_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_jdbc_configs_updated_at
    BEFORE UPDATE ON jdbc_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

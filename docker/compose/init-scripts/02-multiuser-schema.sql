-- ============================================
-- C2JAVA 멀티유저 스키마 확장
-- ============================================

\c c2java;

-- 사용자 테이블
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 애플리케이션 설정 테이블
CREATE TABLE IF NOT EXISTS app_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(255) UNIQUE NOT NULL,
    config_value TEXT,
    config_type VARCHAR(50) DEFAULT 'STRING',
    category VARCHAR(100),
    description TEXT,
    is_sensitive BOOLEAN DEFAULT FALSE,
    is_editable BOOLEAN DEFAULT TRUE,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- conversion_jobs 테이블에 멀티유저 컬럼 추가
ALTER TABLE conversion_jobs 
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS jdbc_config TEXT,
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(100);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_app_configs_key ON app_configs(config_key);
CREATE INDEX IF NOT EXISTS idx_app_configs_category ON app_configs(category);
CREATE INDEX IF NOT EXISTS idx_conversion_jobs_user_id ON conversion_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_conversion_jobs_priority ON conversion_jobs(priority DESC);

-- 기본 관리자 계정 생성 (비밀번호: admin123 - BCrypt 해시)
-- BCrypt 해시는 $2a$10$로 시작하는 60자 문자열
INSERT INTO users (username, password, email, display_name, role, is_active)
VALUES (
    'admin',
    '$2a$10$rDkPvvAFV6kqYQXgBvjGO.8cSKJLApqxiP3k8/N8OaQJxLqE5/Etu',
    'admin@c2java.local',
    '시스템 관리자',
    'ADMIN',
    TRUE
) ON CONFLICT (username) DO NOTHING;

-- 테스트 사용자 계정 (비밀번호: user123)
INSERT INTO users (username, password, email, display_name, role, is_active)
VALUES (
    'user',
    '$2a$10$rDkPvvAFV6kqYQXgBvjGO.8cSKJLApqxiP3k8/N8OaQJxLqE5/Etu',
    'user@c2java.local',
    '테스트 사용자',
    'USER',
    TRUE
) ON CONFLICT (username) DO NOTHING;

-- 기본 설정 값 입력
INSERT INTO app_configs (config_key, config_value, config_type, category, description, is_sensitive, is_editable) VALUES
    ('llm.active_provider', 'qwen3', 'STRING', 'LLM', '활성 LLM 제공자 (qwen3 | gpt_oss)', FALSE, TRUE),
    ('llm.qwen3.api_url', 'http://llm-server:8000/v1', 'URL', 'LLM', 'QWEN3 API URL', FALSE, TRUE),
    ('llm.qwen3.api_key', '', 'SECRET', 'LLM', 'QWEN3 API Key', TRUE, TRUE),
    ('llm.qwen3.model_name', 'qwen3-vl-235b', 'STRING', 'LLM', 'QWEN3 모델명', FALSE, TRUE),
    ('llm.qwen3.max_tokens', '8192', 'NUMBER', 'LLM', 'QWEN3 최대 토큰 수', FALSE, TRUE),
    ('llm.qwen3.temperature', '0.1', 'NUMBER', 'LLM', 'QWEN3 Temperature', FALSE, TRUE),
    ('llm.gpt_oss.api_url', 'http://gpt-server:8001/v1', 'URL', 'LLM', 'GPT OSS API URL', FALSE, TRUE),
    ('llm.gpt_oss.api_key', '', 'SECRET', 'LLM', 'GPT OSS API Key', TRUE, TRUE),
    ('llm.gpt_oss.model_name', 'gpt-oss', 'STRING', 'LLM', 'GPT OSS 모델명', FALSE, TRUE),
    ('cli.aider.enabled', 'true', 'BOOLEAN', 'CLI', 'AIDER 활성화 여부', FALSE, TRUE),
    ('cli.fabric.enabled', 'true', 'BOOLEAN', 'CLI', 'Fabric 활성화 여부', FALSE, TRUE),
    ('server.cli.url', 'http://cli-service:8083', 'URL', 'SERVER', 'CLI 서비스 URL', FALSE, TRUE),
    ('server.mcp.url', 'http://mcp-server:8082', 'URL', 'SERVER', 'MCP 서버 URL', FALSE, TRUE),
    ('conversion.max_concurrent_jobs', '5', 'NUMBER', 'CONVERSION', '최대 동시 변환 작업 수', FALSE, TRUE),
    ('conversion.compile.max_retries', '3', 'NUMBER', 'CONVERSION', '컴파일 최대 재시도 횟수', FALSE, TRUE)
ON CONFLICT (config_key) DO NOTHING;

-- 설정 변경 이력 테이블
CREATE TABLE IF NOT EXISTS config_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(255) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 설정 변경 트리거
CREATE OR REPLACE FUNCTION log_config_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.config_value IS DISTINCT FROM NEW.config_value THEN
        INSERT INTO config_audit_logs (config_key, old_value, new_value, changed_by)
        VALUES (NEW.config_key, OLD.config_value, NEW.config_value, NEW.updated_by);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS config_audit_trigger ON app_configs;
CREATE TRIGGER config_audit_trigger
    AFTER UPDATE ON app_configs
    FOR EACH ROW
    EXECUTE FUNCTION log_config_changes();

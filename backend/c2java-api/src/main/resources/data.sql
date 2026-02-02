-- 기본 관리자 계정 (비밀번호: admin123)
INSERT INTO users (id, username, password, email, display_name, role, is_active, created_at, updated_at)
VALUES (
    RANDOM_UUID(),
    'admin',
    '$2a$10$rDkPvvAFV6kqYQXgBvjGO.8cSKJLApqxiP3k8/N8OaQJxLqE5/Etu',
    'admin@c2java.local',
    '시스템 관리자',
    'ADMIN',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 테스트 사용자 (비밀번호: user123)
INSERT INTO users (id, username, password, email, display_name, role, is_active, created_at, updated_at)
VALUES (
    RANDOM_UUID(),
    'user',
    '$2a$10$rDkPvvAFV6kqYQXgBvjGO.8cSKJLApqxiP3k8/N8OaQJxLqE5/Etu',
    'user@c2java.local',
    '테스트 사용자',
    'USER',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 기본 설정
INSERT INTO app_configs (id, config_key, config_value, category, description, is_secret, is_editable, created_at, updated_at)
VALUES 
(RANDOM_UUID(), 'llm.active_provider', 'qwen3', 'LLM', '활성 LLM 제공자', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(RANDOM_UUID(), 'llm.qwen3.api_url', 'http://llm-server:8080/v1', 'LLM', 'QWEN3 API URL', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(RANDOM_UUID(), 'cli.aider.enabled', 'true', 'CLI', 'AIDER 활성화', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(RANDOM_UUID(), 'cli.fabric.enabled', 'true', 'CLI', 'Fabric 활성화', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

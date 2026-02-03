package com.c2java.service;

import com.c2java.config.CliProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * CLI 서비스
 * AIDER, Fabric, Cursor CLI, OpenAI 등의 CLI 도구와 연동합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CliService {

    private final CliProperties cliProperties;
    private final EnvSyncService envSyncService;

    @Value("${cli.cursor.enabled:false}")
    private boolean cursorCliEnabled;

    @Value("${cli.cursor.path:/usr/local/bin/cursor}")
    private String cursorCliPath;

    @Value("${cli.openai.enabled:false}")
    private boolean openaiEnabled;

    /**
     * Fabric을 사용한 코드 분석
     */
    public String analyzeWithFabric(String filePath) throws IOException {
        if (!cliProperties.getFabric().isEnabled()) {
            log.info("Fabric is disabled, skipping CLI analysis");
            return null;
        }

        log.info("Analyzing file with Fabric: {}", filePath);
        
        CommandLine cmdLine = new CommandLine(cliProperties.getFabric().getExecutablePath());
        cmdLine.addArgument("--pattern");
        cmdLine.addArgument(cliProperties.getFabric().getDefaultPattern());
        cmdLine.addArgument("--file");
        cmdLine.addArgument(filePath);

        return executeCommand(cmdLine);
    }

    /**
     * AIDER를 사용한 코드 변환
     */
    public String convertWithAider(String filePath, String outputPath, String instructions) throws IOException {
        if (!cliProperties.getAider().isEnabled()) {
            log.info("Aider is disabled, skipping CLI conversion");
            return null;
        }

        log.info("Converting file with Aider: {}", filePath);
        
        CommandLine cmdLine = new CommandLine(cliProperties.getAider().getExecutablePath());
        cmdLine.addArgument("--file");
        cmdLine.addArgument(filePath);
        cmdLine.addArgument("--message");
        cmdLine.addArgument(instructions, false);
        
        if (!cliProperties.getAider().isAutoCommits()) {
            cmdLine.addArgument("--no-auto-commits");
        }

        return executeCommand(cmdLine);
    }

    /**
     * 컴파일 오류 수정 요청 (AIDER)
     */
    public String fixCompileErrors(String filePath, String errorLog) throws IOException {
        if (!cliProperties.getAider().isEnabled()) {
            return null;
        }

        log.info("Fixing compile errors with Aider for: {}", filePath);
        
        String instructions = "Fix the following compile errors:\n" + errorLog;
        return convertWithAider(filePath, null, instructions);
    }

    /**
     * Cursor CLI를 사용한 코드 변환 (외부망 전용)
     * Cursor CLI 실제 API: agent -p "prompt" --workspace /path --output-format json --model gpt-4
     */
    public String convertWithCursorCli(String sourceFilePath, String instructions) throws IOException {
        Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
        boolean enabled = "true".equalsIgnoreCase(cliConfig.get("CURSOR_CLI_ENABLED"));
        
        if (!enabled) {
            log.info("Cursor CLI is disabled");
            return null;
        }

        log.info("Converting file with Cursor CLI: {}", sourceFilePath);
        
        try {
            // Cursor CLI 환경변수 로드
            String model = cliConfig.getOrDefault("CURSOR_CLI_MODEL", "gpt-4");
            String authToken = cliConfig.get("CURSOR_CLI_AUTH_TOKEN");
            String workspacePath = cliConfig.get("WORKSPACE_PATH");
            
            Path workspaceDir = workspacePath != null ? Path.of(workspacePath) : Path.of(sourceFilePath).getParent();
            
            // agent --print "instructions" --model gpt-4 [--api-key token]
            CommandLine cmdLine = new CommandLine("/Users/dongsoo/.local/bin/agent");
            cmdLine.addArgument("--print"); // print mode (non-interactive)
            cmdLine.addArgument(instructions, false); // quote=false to preserve special chars
            cmdLine.addArgument("--model");
            cmdLine.addArgument(model);
            
            // API 키가 있으면 추가 (선택사항 - Cursor IDE 실행 중이면 자동 인증)
            if (authToken != null && !authToken.isEmpty()) {
                cmdLine.addArgument("--api-key");
                cmdLine.addArgument(authToken);
            }
            
            log.info("Executing Cursor Agent: model={}, workspace={}", model, workspaceDir);
            return executeCommand(cmdLine);
        } catch (IOException e) {
            log.warn("Cursor CLI execution failed, will fallback to direct LLM API", e);
            return null;
        }
    }

    /**
     * Claude CLI를 사용한 코드 변환 (외부망 전용)
     */
    public String convertWithClaudeCli(String sourceFilePath, String instructions) throws IOException {
        Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
        boolean enabled = "true".equalsIgnoreCase(cliConfig.get("CLAUDE_CLI_ENABLED"));
        
        if (!enabled) {
            log.info("Claude CLI is disabled");
            return null;
        }

        log.info("Converting file with Claude CLI (Anthropic API): {}", sourceFilePath);
        
        try {
            String apiKey = cliConfig.get("ANTHROPIC_API_KEY");
            String model = cliConfig.getOrDefault("CLAUDE_CLI_MODEL", "claude-3-5-sonnet-20240620");
            
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("Anthropic API key not configured");
                return null;
            }
            
            // Claude API 직접 호출 (HTTP)
            WebClient client = WebClient.builder()
                    .baseUrl("https://api.anthropic.com/v1")
                    .defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", "2023-06-01")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // 소스 파일 읽기
            String sourceCode = Files.readString(Path.of(sourceFilePath));
            
            String fullPrompt = String.format("""
                    Convert the following C code to Java Spring Boot 3.2.5:
                    
                    ```c
                    %s
                    ```
                    
                    %s
                    """, sourceCode, instructions);

            Map<String, Object> request = Map.of(
                    "model", model,
                    "max_tokens", 8192,
                    "messages", new Object[]{
                            Map.of("role", "user", "content", fullPrompt)
                    }
            );

            Map<String, Object> response = client.post()
                    .uri("/messages")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                java.util.List<Map<String, Object>> content = 
                    (java.util.List<Map<String, Object>>) response.get("content");
                if (content != null && !content.isEmpty()) {
                    return (String) content.get(0).get("text");
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            return null;
        }
    }

    /**
     * OpenAI API 직접 호출 (외부망 전용)
     */
    public String convertWithOpenAi(String sourceCode, String conversionRules, String instructions) {
        if (!openaiEnabled) {
            log.info("OpenAI CLI is disabled");
            return null;
        }

        try {
            Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
            String apiKey = cliConfig.get("OPENAI_API_KEY");
            String model = cliConfig.getOrDefault("OPENAI_MODEL", "gpt-4");

            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("OpenAI API key not configured");
                return null;
            }

            log.info("Converting code with OpenAI: {}", model);

            WebClient client = WebClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            String fullPrompt = String.format("""
                    [변환 규칙]
                    %s
                    
                    [원본 C 코드]
                    ```c
                    %s
                    ```
                    
                    [요청사항]
                    %s
                    """, conversionRules, sourceCode, instructions);

            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", new Object[]{
                            Map.of("role", "user", "content", fullPrompt)
                    },
                    "max_tokens", 8192,
                    "temperature", 0.1
            );

            Map<String, Object> response = client.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                java.util.List<Map<String, Object>> choices = 
                    (java.util.List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }

            return null;
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return null;
        }
    }

    /**
     * 활성 CLI 도구로 변환 (자동 선택)
     */
    public String convertWithActiveCli(String sourceFilePath, String sourceCode, 
                                      String conversionRules, String instructions) throws IOException {
        Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
        String activeTool = cliConfig.getOrDefault("ACTIVE_CLI_TOOL", "aider");

        log.info("Using active CLI tool: {}", activeTool);

        return switch (activeTool.toLowerCase()) {
            case "cursor" -> convertWithCursorCli(sourceFilePath, instructions);
            case "claude" -> convertWithClaudeCli(sourceFilePath, instructions);
            case "openai" -> convertWithOpenAi(sourceCode, conversionRules, instructions);
            case "aider" -> convertWithAider(sourceFilePath, null, instructions);
            case "fabric" -> analyzeWithFabric(sourceFilePath);
            default -> {
                log.warn("Unknown CLI tool: {}, falling back to AIDER", activeTool);
                yield convertWithAider(sourceFilePath, null, instructions);
            }
        };
    }

    /**
     * 명령어 실행
     */
    private String executeCommand(CommandLine cmdLine) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        
        DefaultExecutor executor = DefaultExecutor.builder().get();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
        executor.setStreamHandler(streamHandler);
        
        try {
            int exitCode = executor.execute(cmdLine);
            String output = outputStream.toString(StandardCharsets.UTF_8);
            
            if (exitCode != 0) {
                String error = errorStream.toString(StandardCharsets.UTF_8);
                log.warn("Command exited with code {}: {}", exitCode, error);
            }
            
            return output;
        } catch (IOException e) {
            String error = errorStream.toString(StandardCharsets.UTF_8);
            log.error("Command execution failed: {}", error, e);
            throw e;
        }
    }
}

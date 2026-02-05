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
import java.util.HashMap;
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
     * stdin을 통해 Cursor agent를 실행합니다.
     */
    public String convertWithCursorCli(String sourceFilePath, String conversionRules, String instructions) throws IOException {
        return convertWithCursorCli(sourceFilePath, conversionRules, instructions, null);
    }
    
    public String convertWithCursorCli(String sourceFilePath, String conversionRules, String instructions, StringBuilder logBuilder) throws IOException {
        Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
        boolean enabled = "true".equalsIgnoreCase(cliConfig.get("CURSOR_CLI_ENABLED"));
        
        if (!enabled) {
            log.info("Cursor CLI is disabled");
            return null;
        }

        log.info("Converting file with Cursor CLI: {}", sourceFilePath);
        
        try {
            // 소스 파일 읽기
            String sourceCode = Files.readString(Path.of(sourceFilePath));
            
            // Cursor CLI 환경변수 로드
            String agentPath = cliConfig.getOrDefault("CURSOR_AGENT_PATH", "/Users/dongsoo/.local/bin/agent");
            String model = cliConfig.getOrDefault("CURSOR_CLI_MODEL", "opus-4.5-thinking");
            
            // 프롬프트 생성 (변환 규칙 포함)
            String fullPrompt = String.format("""
                    Convert the following C code to Java Spring Boot 3.2.5 following these conversion rules:
                    
                    [CONVERSION RULES]
                    %s
                    
                    [C CODE]
                    ```c
                    %s
                    ```
                    
                    [REQUIREMENTS]
                    %s
                    """, conversionRules, sourceCode, instructions);

            // 로그 기록
            if (logBuilder != null) {
                logBuilder.append("\n═══════════════════════════════════════\n");
                logBuilder.append("🤖 Cursor CLI 질의 시작\n");
                logBuilder.append("═══════════════════════════════════════\n");
                logBuilder.append("모델: ").append(model).append("\n");
                logBuilder.append("파일: ").append(sourceFilePath).append("\n");
                logBuilder.append("\n[프롬프트 내용]\n");
                logBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                // 프롬프트가 너무 길면 요약
                if (fullPrompt.length() > 1000) {
                    logBuilder.append(fullPrompt.substring(0, 500)).append("\n...(중략)...\n").append(fullPrompt.substring(fullPrompt.length() - 500));
                } else {
                    logBuilder.append(fullPrompt);
                }
                logBuilder.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            }

            // Cursor agent 실행 (stdin으로 입력 전달, 모델 지정)
            String[] command = {agentPath, "-p", "--model", model};
            
            log.info("Executing Cursor agent with model: {} (stdin input)", model);
            
            String result = executeCommandWithStdin(command, fullPrompt);
            
            // 응답 로그 기록
            if (logBuilder != null) {
                logBuilder.append("\n[AI 응답]\n");
                logBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                if (result != null && !result.isEmpty()) {
                    // 응답이 너무 길면 요약
                    if (result.length() > 1000) {
                        logBuilder.append(result.substring(0, 500)).append("\n...(중략)...\n").append(result.substring(result.length() - 500));
                    } else {
                        logBuilder.append(result);
                    }
                    logBuilder.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    logBuilder.append("✅ Cursor CLI 응답 성공\n");
                } else {
                    logBuilder.append("⚠️ 응답 없음\n");
                    logBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                }
                logBuilder.append("═══════════════════════════════════════\n\n");
            }
            
            if (result != null && !result.isEmpty()) {
                log.info("Cursor agent conversion successful");
                return result;
            } else {
                log.warn("Cursor agent returned empty result");
                return null;
            }
        } catch (Exception e) {
            log.warn("Cursor CLI execution failed, will fallback to direct LLM API", e);
            if (logBuilder != null) {
                logBuilder.append("\n❌ Cursor CLI 오류: ").append(e.getMessage()).append("\n");
                logBuilder.append("═══════════════════════════════════════\n\n");
            }
            return null;
        }
    }

    /**
     * Claude CLI를 사용한 코드 변환 (외부망 전용)
     */
    public String convertWithClaudeCli(String sourceFilePath, String conversionRules, String instructions) throws IOException {
        return convertWithClaudeCli(sourceFilePath, conversionRules, instructions, null);
    }
    
    public String convertWithClaudeCli(String sourceFilePath, String conversionRules, String instructions, StringBuilder logBuilder) throws IOException {
        Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
        boolean enabled = "true".equalsIgnoreCase(cliConfig.get("CLAUDE_CLI_ENABLED"));
        
        if (!enabled) {
            log.info("Claude CLI is disabled");
            return null;
        }

        log.info("Converting file with Claude CLI (Anthropic API): {}", sourceFilePath);
        
        try {
            String apiKey = cliConfig.get("ANTHROPIC_API_KEY");
            String model = cliConfig.getOrDefault("CLAUDE_CLI_MODEL", "claude-opus-4-5-20251101");
            
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
                    Convert the following C code to Java Spring Boot 3.2.5 following these conversion rules:
                    
                    [CONVERSION RULES]
                    %s
                    
                    [C CODE]
                    ```c
                    %s
                    ```
                    
                    [REQUIREMENTS]
                    %s
                    """, conversionRules, sourceCode, instructions);

            // 로그 기록
            if (logBuilder != null) {
                logBuilder.append("\n═══════════════════════════════════════\n");
                logBuilder.append("🤖 Claude API 질의 시작\n");
                logBuilder.append("═══════════════════════════════════════\n");
                logBuilder.append("모델: ").append(model).append("\n");
                logBuilder.append("파일: ").append(sourceFilePath).append("\n");
                logBuilder.append("\n[프롬프트 내용]\n");
                logBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                if (fullPrompt.length() > 1000) {
                    logBuilder.append(fullPrompt.substring(0, 500)).append("\n...(중략)...\n").append(fullPrompt.substring(fullPrompt.length() - 500));
                } else {
                    logBuilder.append(fullPrompt);
                }
                logBuilder.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            }

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

            String result = null;
            if (response != null) {
                java.util.List<Map<String, Object>> content = 
                    (java.util.List<Map<String, Object>>) response.get("content");
                if (content != null && !content.isEmpty()) {
                    result = (String) content.get(0).get("text");
                }
            }

            // 응답 로그 기록
            if (logBuilder != null) {
                logBuilder.append("\n[AI 응답]\n");
                logBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                if (result != null && !result.isEmpty()) {
                    if (result.length() > 1000) {
                        logBuilder.append(result.substring(0, 500)).append("\n...(중략)...\n").append(result.substring(result.length() - 500));
                    } else {
                        logBuilder.append(result);
                    }
                    logBuilder.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    logBuilder.append("✅ Claude API 응답 성공\n");
                } else {
                    logBuilder.append("⚠️ 응답 없음\n");
                    logBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                }
                logBuilder.append("═══════════════════════════════════════\n\n");
            }

            return result;
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            if (logBuilder != null) {
                logBuilder.append("\n❌ Claude API 오류: ").append(e.getMessage()).append("\n");
                logBuilder.append("═══════════════════════════════════════\n\n");
            }
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
        return convertWithActiveCli(sourceFilePath, sourceCode, conversionRules, instructions, null);
    }
    
    public String convertWithActiveCli(String sourceFilePath, String sourceCode, 
                                      String conversionRules, String instructions, StringBuilder logBuilder) throws IOException {
        Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
        String activeTool = cliConfig.getOrDefault("ACTIVE_CLI_TOOL", "aider");

        log.info("Using active CLI tool: {}", activeTool);
        
        if (logBuilder != null) {
            logBuilder.append("\n🔧 활성 CLI 도구: ").append(activeTool).append("\n");
        }

        return switch (activeTool.toLowerCase()) {
            case "cursor" -> convertWithCursorCli(sourceFilePath, conversionRules, instructions, logBuilder);
            case "claude" -> convertWithClaudeCli(sourceFilePath, conversionRules, instructions, logBuilder);
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
    
    /**
     * CLI 연결 테스트 - 현재 시간 질의
     * 외부망 환경설정에서 CLI 도구가 정상 작동하는지 테스트합니다.
     */
    public Map<String, Object> testCliConnection(String cliTool) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("cliTool", cliTool);
        result.put("timestamp", java.time.Instant.now().toString());
        
        try {
            Map<String, String> cliConfig = envSyncService.loadCliEnvVariables();
            String testPrompt = "What time is it now? Please respond with the current date and time in a friendly way.";
            
            long startTime = System.currentTimeMillis();
            String response = null;
            
            switch (cliTool.toLowerCase()) {
                case "cursor" -> {
                    String agentPath = cliConfig.getOrDefault("CURSOR_AGENT_PATH", "/Users/dongsoo/.local/bin/agent");
                    String model = cliConfig.getOrDefault("CURSOR_CLI_MODEL", "opus-4.5-thinking");
                    String[] command = {agentPath, "-p", "--model", model};
                    log.info("Testing Cursor CLI connection with agent: {} (model: {})", agentPath, model);
                    response = executeCommandWithStdin(command, testPrompt);
                }
                case "claude" -> {
                    String apiKey = cliConfig.get("ANTHROPIC_API_KEY");
                    String model = cliConfig.getOrDefault("CLAUDE_CLI_MODEL", "claude-opus-4-5-20251101");
                    
                    if (apiKey == null || apiKey.isEmpty()) {
                        throw new IllegalStateException("Anthropic API Key가 설정되지 않았습니다.");
                    }
                    
                    log.info("Testing Claude API connection with model: {}", model);
                    
                    WebClient client = WebClient.builder()
                            .baseUrl("https://api.anthropic.com/v1")
                            .defaultHeader("x-api-key", apiKey)
                            .defaultHeader("anthropic-version", "2023-06-01")
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build();
                    
                    Map<String, Object> request = Map.of(
                            "model", model,
                            "max_tokens", 1024,
                            "messages", new Object[]{
                                    Map.of("role", "user", "content", testPrompt)
                            }
                    );
                    
                    Map<String, Object> apiResponse = client.post()
                            .uri("/messages")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();
                    
                    if (apiResponse != null) {
                        java.util.List<Map<String, Object>> content = 
                            (java.util.List<Map<String, Object>>) apiResponse.get("content");
                        if (content != null && !content.isEmpty()) {
                            response = (String) content.get(0).get("text");
                        }
                    }
                }
                case "openai" -> {
                    String apiKey = cliConfig.get("OPENAI_API_KEY");
                    String model = cliConfig.getOrDefault("OPENAI_MODEL", "gpt-4");
                    
                    if (apiKey == null || apiKey.isEmpty()) {
                        throw new IllegalStateException("OpenAI API Key가 설정되지 않았습니다.");
                    }
                    
                    log.info("Testing OpenAI API connection with model: {}", model);
                    
                    WebClient client = WebClient.builder()
                            .baseUrl("https://api.openai.com/v1")
                            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .build();
                    
                    Map<String, Object> request = Map.of(
                            "model", model,
                            "messages", new Object[]{
                                    Map.of("role", "user", "content", testPrompt)
                            },
                            "max_tokens", 1024
                    );
                    
                    Map<String, Object> apiResponse = client.post()
                            .uri("/chat/completions")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();
                    
                    if (apiResponse != null) {
                        java.util.List<Map<String, Object>> choices = 
                            (java.util.List<Map<String, Object>>) apiResponse.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                            response = (String) message.get("content");
                        }
                    }
                }
                default -> {
                    throw new IllegalArgumentException("지원하지 않는 CLI 도구입니다: " + cliTool);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (response != null && !response.isEmpty()) {
                result.put("success", true);
                result.put("response", response);
                result.put("duration", duration + "ms");
                result.put("message", "연결 테스트 성공");
                log.info("CLI connection test successful for {}: {} ms", cliTool, duration);
            } else {
                result.put("message", "응답이 비어있습니다.");
                log.warn("CLI connection test returned empty response for {}", cliTool);
            }
            
        } catch (Exception e) {
            result.put("message", "연결 실패: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            log.error("CLI connection test failed for {}", cliTool, e);
        }
        
        return result;
    }

    /**
     * 명령어 실행 (stdin 입력 지원) - Cursor agent용
     */
    private String executeCommandWithStdin(String[] command, String input) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // stdin으로 입력 전달
            if (input != null && !input.isEmpty()) {
                try (var writer = process.outputWriter()) {
                    writer.write(input);
                    writer.flush();
                }
            }
            
            // 출력 읽기
            StringBuilder output = new StringBuilder();
            try (var reader = process.inputReader()) {
                reader.lines().forEach(line -> output.append(line).append("\n"));
            }
            
            // 프로세스 종료 대기 (최대 30초 - 테스트용으로 단축)
            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Command timeout after 30 seconds");
            }
            
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return output.toString().trim();
            } else {
                log.error("Command failed with exit code {}: {}", exitCode, output);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Command interrupted", e);
        }
    }
}

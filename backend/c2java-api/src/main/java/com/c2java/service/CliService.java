package com.c2java.service;

import com.c2java.config.CliProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CLI 서비스
 * AIDER, Fabric 등의 CLI 도구와 연동합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CliService {

    private final CliProperties cliProperties;

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

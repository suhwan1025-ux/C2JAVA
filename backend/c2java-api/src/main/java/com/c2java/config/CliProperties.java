package com.c2java.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CLI 도구 설정 프로퍼티
 */
@Configuration
@ConfigurationProperties(prefix = "cli")
@Getter
@Setter
public class CliProperties {

    /**
     * AIDER 설정
     */
    private AiderConfig aider = new AiderConfig();

    /**
     * Fabric 설정
     */
    private FabricConfig fabric = new FabricConfig();

    @Getter
    @Setter
    public static class AiderConfig {
        private boolean enabled = true;
        private String executablePath = "/usr/local/bin/aider";
        private boolean autoCommits = false;
    }

    @Getter
    @Setter
    public static class FabricConfig {
        private boolean enabled = true;
        private String executablePath = "/usr/local/bin/fabric";
        private String defaultPattern = "analyze_code";
    }
}

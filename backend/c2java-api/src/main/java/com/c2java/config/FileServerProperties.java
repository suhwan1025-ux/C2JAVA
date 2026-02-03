package com.c2java.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 파일 서버 설정
 * 별도 가상화 서버에 파일을 저장할 때 사용합니다.
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-server")
public class FileServerProperties {

    /**
     * 파일 서버 사용 여부
     * false면 로컬 저장, true면 원격 서버에 저장
     */
    private boolean enabled = false;

    /**
     * 파일 서버 URL (예: http://file-server:8090)
     */
    private String url = "";

    /**
     * 파일 업로드 엔드포인트 (기본값: /api/files/upload)
     */
    private String uploadEndpoint = "/api/files/upload";

    /**
     * 파일 다운로드 엔드포인트 (기본값: /api/files/download)
     */
    private String downloadEndpoint = "/api/files/download";

    /**
     * 인증 토큰 (선택사항)
     */
    private String authToken = "";

    /**
     * 연결 타임아웃 (초)
     */
    private int connectTimeout = 30;

    /**
     * 읽기 타임아웃 (초)
     */
    private int readTimeout = 60;

    /**
     * 로컬 저장 경로 (파일 서버 비활성화 시 사용)
     */
    private String localPath = "/app/workspace";
}

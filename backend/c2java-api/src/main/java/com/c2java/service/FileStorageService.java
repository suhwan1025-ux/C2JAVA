package com.c2java.service;

import com.c2java.config.FileServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 파일 저장 서비스
 * 로컬 또는 원격 파일 서버에 파일을 저장합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileServerProperties fileServerProperties;

    /**
     * 파일 업로드 (자동으로 로컬/원격 선택)
     * @return 저장된 파일 경로 또는 URL
     */
    public String uploadFile(MultipartFile file, String jobId) throws IOException {
        if (fileServerProperties.isEnabled() && !fileServerProperties.getUrl().isEmpty()) {
            return uploadToRemoteServer(file, jobId);
        } else {
            return saveToLocal(file, jobId);
        }
    }

    /**
     * 파일 업로드 (바이트 배열)
     */
    public String uploadFile(byte[] content, String fileName, String jobId) throws IOException {
        if (fileServerProperties.isEnabled() && !fileServerProperties.getUrl().isEmpty()) {
            return uploadToRemoteServer(content, fileName, jobId);
        } else {
            return saveToLocal(content, fileName, jobId);
        }
    }

    /**
     * 원격 파일 서버에 업로드
     */
    private String uploadToRemoteServer(MultipartFile file, String jobId) throws IOException {
        return uploadToRemoteServer(file.getBytes(), file.getOriginalFilename(), jobId);
    }

    /**
     * 원격 파일 서버에 업로드 (바이트 배열)
     */
    private String uploadToRemoteServer(byte[] content, String fileName, String jobId) throws IOException {
        String serverUrl = fileServerProperties.getUrl();
        String uploadEndpoint = fileServerProperties.getUploadEndpoint();
        String fullUrl = serverUrl + uploadEndpoint;

        log.info("Uploading file to remote server: {} -> {}", fileName, fullUrl);

        try {
            RestTemplate restTemplate = createRestTemplate();

            // Multipart 요청 생성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            if (!fileServerProperties.getAuthToken().isEmpty()) {
                headers.set("Authorization", "Bearer " + fileServerProperties.getAuthToken());
            }

            // 파일 리소스 생성
            ByteArrayResource fileResource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("jobId", jobId);
            body.add("fileName", fileName);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 서버에서 반환한 파일 경로/URL
                String remotePath = (String) response.getBody().get("path");
                if (remotePath == null) {
                    remotePath = (String) response.getBody().get("url");
                }
                if (remotePath == null) {
                    remotePath = serverUrl + "/files/" + jobId + "/" + fileName;
                }
                log.info("File uploaded successfully: {}", remotePath);
                return remotePath;
            } else {
                throw new IOException("Failed to upload file to remote server: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to upload to remote server, falling back to local storage: {}", e.getMessage());
            // 실패 시 로컬 저장으로 폴백
            return saveToLocal(content, fileName, jobId);
        }
    }

    /**
     * 로컬 파일 시스템에 저장
     */
    private String saveToLocal(MultipartFile file, String jobId) throws IOException {
        return saveToLocal(file.getBytes(), file.getOriginalFilename(), jobId);
    }

    /**
     * 로컬 파일 시스템에 저장 (바이트 배열)
     */
    private String saveToLocal(byte[] content, String fileName, String jobId) throws IOException {
        String localPath = fileServerProperties.getLocalPath();
        Path uploadPath = Paths.get(localPath, "uploads", jobId);
        Files.createDirectories(uploadPath);

        Path filePath = uploadPath.resolve(fileName);
        Files.write(filePath, content);

        log.info("File saved locally: {}", filePath);
        return filePath.toString();
    }

    /**
     * 파일 다운로드 (원격 서버에서)
     */
    public byte[] downloadFile(String filePath) throws IOException {
        if (fileServerProperties.isEnabled() && filePath.startsWith("http")) {
            return downloadFromRemoteServer(filePath);
        } else {
            return Files.readAllBytes(Paths.get(filePath));
        }
    }

    /**
     * 원격 서버에서 파일 다운로드
     */
    private byte[] downloadFromRemoteServer(String fileUrl) throws IOException {
        log.info("Downloading file from remote server: {}", fileUrl);

        try {
            RestTemplate restTemplate = createRestTemplate();

            HttpHeaders headers = new HttpHeaders();
            if (!fileServerProperties.getAuthToken().isEmpty()) {
                headers.set("Authorization", "Bearer " + fileServerProperties.getAuthToken());
            }

            HttpEntity<String> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    fileUrl,
                    HttpMethod.GET,
                    requestEntity,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new IOException("Failed to download file: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to download from remote server: {}", e.getMessage());
            throw new IOException("Failed to download file from remote server", e);
        }
    }

    /**
     * 파일 서버 연결 테스트
     */
    public boolean testConnection() {
        if (!fileServerProperties.isEnabled() || fileServerProperties.getUrl().isEmpty()) {
            return false;
        }

        try {
            RestTemplate restTemplate = createRestTemplate();
            String healthUrl = fileServerProperties.getUrl() + "/health";
            
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("File server connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 파일 서버 상태 정보
     */
    public Map<String, Object> getServerStatus() {
        boolean connected = testConnection();
        return Map.of(
                "enabled", fileServerProperties.isEnabled(),
                "url", fileServerProperties.getUrl(),
                "connected", connected,
                "localPath", fileServerProperties.getLocalPath()
        );
    }

    /**
     * RestTemplate 생성 (타임아웃 설정 포함)
     */
    private RestTemplate createRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // 타임아웃은 별도 설정 필요시 ClientHttpRequestFactory 사용
        return restTemplate;
    }
}

package com.toggle.service;

import com.toggle.global.config.S3Properties;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3FileService {

    private static final Set<String> BUSINESS_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/jpeg",
        "image/png"
    );

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private static final long TEN_MB = 10L * 1024L * 1024L;
    private static final long FIVE_MB = 5L * 1024L * 1024L;
    private static final Duration DEFAULT_PRESIGN_DURATION = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;
    private final Set<String> pendingDeleteKeys = ConcurrentHashMap.newKeySet();

    public S3FileService(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public StoredFile uploadFile(MultipartFile file, String dir) {
        UploadPolicy policy = resolvePolicy(dir);
        validateFile(file, policy);

        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        String key = policy.dir() + "/" + UUID.randomUUID() + "-" + safeFilename;
        String contentType = normalizeContentType(file.getContentType());

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(file.getBytes())
            );
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "파일 저장에 실패했습니다.");
        }

        return new StoredFile(ImageUrlMapper.toBrowserUrl(buildObjectUrl(key)), key);
    }

    public void deleteFile(String key) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey.isBlank()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteOrQueue(normalizedKey);
                }
            });
            return;
        }

        deleteOrQueue(normalizedKey);
    }

    public void deleteFilesAfterCommit(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        List<String> normalizedKeys = keys.stream()
            .map(this::normalizeKey)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toList());

        if (normalizedKeys.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            List<String> committedKeys = normalizedKeys.stream()
                .distinct()
                .toList();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedKeys.forEach(S3FileService.this::deleteOrQueue);
                }
            });
            return;
        }

        normalizedKeys.stream().distinct().forEach(this::deleteOrQueue);
    }

    public String createPresignedGetUrl(String key) {
        return createPresignedGetUrl(key, DEFAULT_PRESIGN_DURATION);
    }

    public String createPresignedGetUrl(String key, Duration duration) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_KEY", "파일 키가 올바르지 않습니다.");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket())
            .key(normalizedKey)
            .build();

        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
            .signatureDuration(duration)
            .getObjectRequest(getObjectRequest)
            .build())
            .url()
            .toString();
    }

    private void validateFile(MultipartFile file, UploadPolicy policy) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "파일을 첨부해 주세요.");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_CONTENT_TYPE", policy.displayName() + " 파일의 Content-Type을 확인할 수 없습니다.");
        }
        if (!policy.allowedContentTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", policy.displayName() + "에 허용되지 않는 파일 형식입니다.");
        }

        if (file.getSize() > policy.maxSizeBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", policy.displayName() + "의 파일 크기 제한을 초과했습니다.");
        }
    }

    private UploadPolicy resolvePolicy(String dir) {
        String normalizedDir = normalizeDir(dir);
        return switch (normalizedDir) {
            case "business" -> new UploadPolicy("business", BUSINESS_CONTENT_TYPES, TEN_MB, "사업자등록증");
            case "menu" -> new UploadPolicy("menu", IMAGE_CONTENT_TYPES, FIVE_MB, "메뉴 이미지");
            case "review" -> new UploadPolicy("review", IMAGE_CONTENT_TYPES, FIVE_MB, "리뷰 이미지");
            case "store" -> new UploadPolicy("store", IMAGE_CONTENT_TYPES, FIVE_MB, "매장 이미지");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD_DIRECTORY", "지원하지 않는 업로드 경로입니다.");
        };
    }

    private String buildObjectUrl(String key) {
        String encodedKey = Arrays.stream(normalizeKey(key).split("/"))
            .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(Collectors.joining("/"));
        return "https://" + bucket() + ".s3." + region() + ".amazonaws.com/" + encodedKey;
    }

    private String sanitizeFilename(@Nullable String originalFilename) {
        String candidate = originalFilename == null ? "file" : originalFilename;
        String filename = Path.of(candidate).getFileName().toString().trim();
        if (filename.isBlank()) {
            filename = "file";
        }

        String sanitized = filename
            .replaceAll("\\s+", "_")
            .replaceAll("[^A-Za-z0-9._-]", "_");

        return sanitized.isBlank() ? "file" : sanitized;
    }

    private String normalizeContentType(@Nullable String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKey(@Nullable String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isBlank()) {
            return "";
        }

        String objectKey = ImageUrlMapper.toObjectKey(normalized);
        if (objectKey != null && !objectKey.isBlank()) {
            return objectKey;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return "";
        }

        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private String normalizeDir(@Nullable String dir) {
        String normalized = normalizeKey(dir);
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String bucket() {
        return properties.s3().bucket().trim();
    }

    private String region() {
        return properties.region().trim();
    }

    private void deleteOrQueue(String normalizedKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket())
                .key(normalizedKey)
                .build());
            pendingDeleteKeys.remove(normalizedKey);
        } catch (RuntimeException ex) {
            pendingDeleteKeys.add(normalizedKey);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryPendingDeletes() {
        for (String pendingKey : pendingDeleteKeys.toArray(new String[0])) {
            if (pendingDeleteKeys.remove(pendingKey)) {
                deleteOrQueue(pendingKey);
            }
        }
    }

    private record UploadPolicy(
        String dir,
        Set<String> allowedContentTypes,
        long maxSizeBytes,
        String displayName
    ) {
    }

    public record StoredFile(String url, String key) {
    }
}

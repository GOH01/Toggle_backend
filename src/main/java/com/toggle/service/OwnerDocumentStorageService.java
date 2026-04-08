package com.toggle.service;

import com.toggle.global.config.OwnerDocumentStorageProperties;
import com.toggle.global.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OwnerDocumentStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/jpeg",
        "image/png"
    );

    private final Path uploadDir;

    public OwnerDocumentStorageService(OwnerDocumentStorageProperties properties) {
        this.uploadDir = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    public StoredOwnerDocument store(MultipartFile file) {
        validateFile(file);

        try {
            Files.createDirectories(uploadDir);
            String originalName = file.getOriginalFilename() == null ? "document" : Path.of(file.getOriginalFilename()).getFileName().toString();
            String storedName = UUID.randomUUID() + "-" + originalName.replace(" ", "_");
            Path targetPath = uploadDir.resolve(storedName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredOwnerDocument(
                targetPath.toString(),
                originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType()
            );
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "사업자 등록증 파일 저장에 실패했습니다.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUSINESS_LICENSE_REQUIRED", "사업자 등록증 파일을 첨부해 주세요.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BUSINESS_LICENSE_FILE", "PDF 또는 JPG/PNG 파일만 업로드할 수 있습니다.");
        }
    }

    public record StoredOwnerDocument(
        String storedPath,
        String originalName,
        String contentType
    ) {
    }
}

package com.toggle.controller;

import com.toggle.dto.file.FileUploadResponse;
import com.toggle.global.response.ApiResponse;
import com.toggle.service.S3FileService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private final S3FileService s3FileService;

    public FileUploadController(S3FileService s3FileService) {
        this.s3FileService = s3FileService;
    }

    @PostMapping(value = "/business", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadBusinessFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(toResponse(s3FileService.uploadFile(file, "business"), file.getContentType()));
    }

    @PostMapping(value = "/menu", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadMenuFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(toResponse(s3FileService.uploadFile(file, "menu"), file.getContentType()));
    }

    @PostMapping(value = "/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadReviewFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(toResponse(s3FileService.uploadFile(file, "review"), file.getContentType()));
    }

    @PostMapping(value = "/store", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> uploadStoreFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(toResponse(s3FileService.uploadFile(file, "store"), file.getContentType()));
    }

    @GetMapping("/view")
    public ResponseEntity<Void> viewFile(@RequestParam("key") String key) {
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(s3FileService.createPresignedGetUrl(key)))
            .build();
    }

    private FileUploadResponse toResponse(S3FileService.StoredFile storedFile, String contentType) {
        return new FileUploadResponse(storedFile.url(), storedFile.key(), contentType);
    }
}

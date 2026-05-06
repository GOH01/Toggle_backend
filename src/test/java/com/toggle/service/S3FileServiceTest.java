package com.toggle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toggle.global.config.S3Properties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@ExtendWith(MockitoExtension.class)
class S3FileServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3FileService service;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
            new S3Properties.Credentials("test-access-key", "test-secret-key"),
            "ap-northeast-2",
            new S3Properties.S3Bucket("sku-toggle")
        );
        service = new S3FileService(s3Client, s3Presigner, properties);
    }

    @Test
    void uploadFileShouldGenerateUuidKeyAndSanitizeFilename() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().eTag("etag").build());

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../../business card.png",
            "image/png",
            "image-bytes".getBytes(StandardCharsets.UTF_8)
        );

        S3FileService.StoredFile result = service.uploadFile(file, "store");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertThat(requestCaptor.getValue().bucket()).isEqualTo("sku-toggle");
        assertThat(requestCaptor.getValue().key()).startsWith("store/");
        assertThat(requestCaptor.getValue().key()).contains("-business_card.png");
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
        assertThat(result.key()).startsWith("store/");
        assertThat(result.url()).startsWith("/api/v1/files/view?key=store%2F");
    }

    @Test
    void uploadFileShouldRejectInvalidBusinessContentType() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "license.txt",
            "text/plain",
            "not-allowed".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.uploadFile(file, "business"))
            .isInstanceOf(com.toggle.global.exception.ApiException.class)
            .extracting(throwable -> ((com.toggle.global.exception.ApiException) throwable).getCode())
            .isEqualTo("INVALID_FILE_TYPE");
    }

    @Test
    void uploadFileShouldRejectOversizedStoreImages() {
        byte[] oversized = new byte[(int) (5L * 1024L * 1024L) + 1];
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "store.png",
            "image/png",
            oversized
        );

        assertThatThrownBy(() -> service.uploadFile(file, "store"))
            .isInstanceOf(com.toggle.global.exception.ApiException.class)
            .extracting(throwable -> ((com.toggle.global.exception.ApiException) throwable).getCode())
            .isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void deleteFileShouldCallS3DeleteObject() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        service.deleteFile("business/sample-key");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}

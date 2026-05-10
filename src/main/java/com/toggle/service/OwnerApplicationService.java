package com.toggle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggle.dto.kakao.KakaoKeywordSearchResponse;
import com.toggle.dto.owner.BusinessVerificationHistoryResponse;
import com.toggle.dto.owner.ExecuteMapVerificationRequest;
import com.toggle.dto.owner.ManualBusinessVerificationRequest;
import com.toggle.dto.owner.NationalTaxVerificationResult;
import com.toggle.dto.owner.OwnerApplicationApproveRequest;
import com.toggle.dto.owner.OwnerApplicationDetailResponse;
import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationResponse;
import com.toggle.dto.owner.OwnerApplicationReviewResponse;
import com.toggle.dto.owner.OwnerApplicationSummaryResponse;
import com.toggle.dto.owner.OwnerApplicationUpdateRequest;
import com.toggle.dto.owner.OwnerLinkedStoreResponse;
import com.toggle.dto.owner.OwnerStoreProfileUpdateRequest;
import com.toggle.dto.owner.OwnerStoreLinkResponse;
import com.toggle.dto.owner.OwnerStoreStatusResponse;
import com.toggle.dto.owner.OwnerStoreStatusUpdateRequest;
import com.toggle.dto.owner.MapVerificationHistoryResponse;
import com.toggle.dto.store.ResolveStoreRequest;
import com.toggle.dto.store.ResolveStoreResponse;
import com.toggle.entity.AdminReviewActionType;
import com.toggle.entity.AdminReviewLog;
import com.toggle.entity.BusinessStatus;
import com.toggle.entity.BusinessVerificationHistory;
import com.toggle.entity.BusinessVerificationStatus;
import com.toggle.entity.BusinessVerificationType;
import com.toggle.entity.ExternalSource;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.MapVerificationHistory;
import com.toggle.entity.MapVerificationQueryType;
import com.toggle.entity.MapVerificationStatus;
import com.toggle.entity.OwnerApplication;
import com.toggle.entity.OwnerApplicationReviewStatus;
import com.toggle.entity.OwnerStoreLink;
import com.toggle.entity.OwnerStoreMatchStatus;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.entity.VerificationRecordStatus;
import com.toggle.global.exception.ApiException;
import com.toggle.global.util.ImageUrlMapper;
import com.toggle.repository.AdminReviewLogRepository;
import com.toggle.repository.BusinessVerificationHistoryRepository;
import com.toggle.repository.MapVerificationHistoryRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OwnerApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OwnerApplicationService.class);
    private static final DateTimeFormatter NTS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int BUSINESS_LICENSE_RETENTION_DAYS = 7;
    private static final Set<String> BUSINESS_LICENSE_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/jpeg",
        "image/png"
    );
    private static final List<OwnerApplicationReviewStatus> ACTIVE_REQUEST_STATUSES = List.of(
        OwnerApplicationReviewStatus.PENDING,
        OwnerApplicationReviewStatus.UNDER_REVIEW
    );

    private final OwnerApplicationRepository ownerApplicationRepository;
    private final OwnerStoreLinkRepository ownerStoreLinkRepository;
    private final BusinessVerificationHistoryRepository businessVerificationHistoryRepository;
    private final MapVerificationHistoryRepository mapVerificationHistoryRepository;
    private final AdminReviewLogRepository adminReviewLogRepository;
    private final S3FileService s3FileService;
    private final AddressNormalizer addressNormalizer;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final StoreEligibilityService storeEligibilityService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final NationalTaxServiceClient nationalTaxServiceClient;
    private final ObjectMapper objectMapper;

    public OwnerApplicationService(
        OwnerApplicationRepository ownerApplicationRepository,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        BusinessVerificationHistoryRepository businessVerificationHistoryRepository,
        MapVerificationHistoryRepository mapVerificationHistoryRepository,
        AdminReviewLogRepository adminReviewLogRepository,
        S3FileService s3FileService,
        AddressNormalizer addressNormalizer,
        StoreRepository storeRepository,
        StoreService storeService,
        StoreEligibilityService storeEligibilityService,
        KakaoPlaceClient kakaoPlaceClient,
        NationalTaxServiceClient nationalTaxServiceClient,
        ObjectMapper objectMapper
    ) {
        this.ownerApplicationRepository = ownerApplicationRepository;
        this.ownerStoreLinkRepository = ownerStoreLinkRepository;
        this.businessVerificationHistoryRepository = businessVerificationHistoryRepository;
        this.mapVerificationHistoryRepository = mapVerificationHistoryRepository;
        this.adminReviewLogRepository = adminReviewLogRepository;
        this.s3FileService = s3FileService;
        this.addressNormalizer = addressNormalizer;
        this.storeRepository = storeRepository;
        this.storeService = storeService;
        this.storeEligibilityService = storeEligibilityService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.nationalTaxServiceClient = nationalTaxServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OwnerApplicationResponse createApplication(User ownerUser, OwnerApplicationRequest request, MultipartFile businessLicenseFile) {
        assertOwner(ownerUser);

        ensureNoActiveDuplicateApplication(ownerUser.getId(), request.businessNumber(), request.businessAddress(), null);
        BusinessLicenseMetadata metadata = requireBusinessLicenseMetadata(businessLicenseFile);
        S3FileService.StoredFile storedFile = s3FileService.uploadFile(businessLicenseFile, "business");
        String storedKey = requireStoredBusinessLicenseKey(storedFile.key());
        OwnerApplication application = buildApplication(ownerUser, request, storedKey, metadata.withStorage(storedKey));
        ownerApplicationRepository.save(application);
        runInitialVerifications(application);

        return toApplicationResponse(application);
    }

    @Transactional
    public OwnerApplicationResponse updateApplication(
        User ownerUser,
        Long applicationId,
        OwnerApplicationUpdateRequest request,
        MultipartFile businessLicenseFile
    ) {
        assertOwner(ownerUser);
        OwnerApplication application = getApplication(applicationId);
        if (!application.getUser().getId().equals(ownerUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_APPLICATION_ACCESS_DENIED", "본인 신청만 수정할 수 있습니다.");
        }
        if (!application.isEditableByOwner()) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_APPLICATION_NOT_EDITABLE", "이미 최종 처리된 신청은 수정할 수 없습니다.");
        }

        ensureNoActiveDuplicateApplication(ownerUser.getId(), request.businessNumber(), request.businessAddress(), applicationId);

        String storedKey = application.getBusinessLicenseObjectKey();
        BusinessLicenseMetadata metadata = BusinessLicenseMetadata.from(application);
        if (businessLicenseFile != null) {
            metadata = requireBusinessLicenseMetadata(businessLicenseFile);
            S3FileService.StoredFile storedFile = s3FileService.uploadFile(businessLicenseFile, "business");
            storedKey = requireStoredBusinessLicenseKey(storedFile.key());
            metadata = metadata.withStorage(storedKey);
            if (application.getBusinessLicenseObjectKey() != null && !application.getBusinessLicenseObjectKey().isBlank()
                && !application.getBusinessLicenseObjectKey().equals(storedKey)) {
                s3FileService.deleteFile(application.getBusinessLicenseObjectKey());
            }
        }

        String normalizedAddress = request.businessAddress().trim();
        application.updateOwnerDraft(
            request.storeName().trim(),
            normalizeBusinessNumber(request.businessNumber()),
            request.representativeName().trim(),
            request.businessOpenDate(),
            normalizedAddress,
            addressNormalizer.normalize(normalizedAddress),
            normalizePhone(request.businessPhone()),
            storedKey,
            metadata.originalName(),
            metadata.originalFilename(),
            metadata.storedPath(),
            metadata.contentType(),
            metadata.size(),
            metadata.uploadedAt(),
            metadata.expiresAt()
        );
        runInitialVerifications(application);
        return toApplicationResponse(application);
    }

    @Transactional(readOnly = true)
    public List<OwnerApplicationSummaryResponse> listMyApplications(Long ownerUserId) {
        return ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(ownerUserId).stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerApplicationSummaryResponse> listApplications() {
        return ownerApplicationRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public OwnerApplicationDetailResponse getApplicationDetail(Long applicationId) {
        OwnerApplication application = getApplication(applicationId);
        return new OwnerApplicationDetailResponse(
            toSummaryResponse(application),
            application.isBusinessLicenseDeleted() ? null : s3FileService.createPresignedGetUrl(application.getBusinessLicenseObjectKey()),
            businessVerificationHistoryRepository.findAllByRequestIdOrderByCreatedAtDesc(applicationId).stream()
                .map(history -> new BusinessVerificationHistoryResponse(
                    history.getVerificationType().name(),
                    history.getStatus().name(),
                    history.getFailureCode(),
                    history.getFailureMessage(),
                    history.getVerifiedAt()
                ))
                .toList(),
            mapVerificationHistoryRepository.findAllByRequestIdOrderByCreatedAtDesc(applicationId).stream()
                .map(history -> new MapVerificationHistoryResponse(
                    history.getQueryText(),
                    history.getStatus().name(),
                    history.getCandidateCount(),
                    history.getSelectedPlaceName(),
                    history.getSelectedRoadAddress(),
                    history.getSelectedJibunAddress(),
                    history.getSelectedExternalPlaceId(),
                    history.getFailureCode(),
                    history.getFailureMessage(),
                    history.getVerifiedAt()
                ))
                .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<OwnerLinkedStoreResponse> listLinkedStores(Long ownerUserId) {
        return ownerStoreLinkRepository.findAllByOwnerUserIdAndStoreDeletedAtIsNull(ownerUserId).stream()
            .map(this::toOwnerLinkedStoreResponse)
            .toList();
    }

    @Transactional
    public void unlinkOwnerStore(User ownerUser, Long storeId) {
        assertOwner(ownerUser);

        OwnerStoreLink link = ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(ownerUser.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OWNER_STORE_LINK_NOT_FOUND", "연결된 매장을 찾을 수 없습니다."));
        ownerStoreLinkRepository.delete(link);
    }

    @Transactional
    public OwnerApplicationSummaryResponse executeBusinessVerification(Long applicationId, User adminUser) {
        assertAdmin(adminUser);
        OwnerApplication application = getApplication(applicationId);
        ensureReviewable(application);
        runBusinessVerification(application);
        adminReviewLogRepository.save(new AdminReviewLog(
            application,
            adminUser,
            AdminReviewActionType.REQUEST_AUTO_VERIFICATION,
            null,
            "{\"result\":\"" + application.getBusinessVerificationStatus().name() + "\"}"
        ));
        return toSummaryResponse(application);
    }

    @Transactional
    public OwnerApplicationSummaryResponse manualVerifyBusiness(Long applicationId, User adminUser, ManualBusinessVerificationRequest request) {
        assertAdmin(adminUser);
        OwnerApplication application = getApplication(applicationId);
        ensureReviewable(application);
        if (application.getBusinessVerificationStatus() != BusinessVerificationStatus.AUTO_VERIFICATION_UNAVAILABLE
            && application.getBusinessVerificationStatus() != BusinessVerificationStatus.AUTO_VERIFICATION_FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "MANUAL_VERIFICATION_NOT_ALLOWED", "자동 검증 실패 또는 불가 상태에서만 수동 보정을 할 수 있습니다.");
        }

        if (request.verified()) {
            application.markManualVerified();
            businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
                application,
                BusinessVerificationType.MANUAL_ADMIN,
                VerificationRecordStatus.SUCCESS,
                null,
                null,
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getBusinessOpenDate().toString(),
                application.getBusinessAddressRaw(),
                null,
                null,
                adminUser,
                LocalDateTime.now()
            ));
            adminReviewLogRepository.save(new AdminReviewLog(
                application,
                adminUser,
                AdminReviewActionType.MARK_MANUAL_VERIFIED,
                request.reason(),
                null
            ));
        } else {
            application.markManualVerificationFailed();
            businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
                application,
                BusinessVerificationType.MANUAL_ADMIN,
                VerificationRecordStatus.FAILED,
                null,
                null,
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getBusinessOpenDate().toString(),
                application.getBusinessAddressRaw(),
                "MANUAL_VERIFICATION_FAILED",
                request.reason(),
                adminUser,
                LocalDateTime.now()
            ));
            adminReviewLogRepository.save(new AdminReviewLog(
                application,
                adminUser,
                AdminReviewActionType.MARK_MANUAL_FAILED,
                request.reason(),
                null
            ));
        }

        return toSummaryResponse(application);
    }

    @Transactional
    public OwnerApplicationSummaryResponse executeMapVerification(Long applicationId, ExecuteMapVerificationRequest request, User adminUser) {
        assertAdmin(adminUser);
        OwnerApplication application = getApplication(applicationId);
        ensureReviewable(application);
        if (!request.forceRefresh() && application.getMapVerificationStatus() != MapVerificationStatus.NOT_STARTED) {
            return toSummaryResponse(application);
        }
        runMapVerification(application);
        adminReviewLogRepository.save(new AdminReviewLog(
            application,
            adminUser,
            AdminReviewActionType.REQUEST_MAP_VERIFICATION,
            null,
            "{\"forceRefresh\":" + request.forceRefresh() + ",\"result\":\"" + application.getMapVerificationStatus().name() + "\"}"
        ));
        return toSummaryResponse(application);
    }

    @Transactional
    public OwnerApplicationReviewResponse approve(Long applicationId, OwnerApplicationApproveRequest request, User adminUser) {
        assertAdmin(adminUser);
        OwnerApplication application = getApplication(applicationId);
        ensureReviewable(application);

        Store verifiedStore = application.getVerifiedStore();
        if (verifiedStore != null && ownerStoreLinkRepository.existsByStoreIdAndStoreDeletedAtIsNull(verifiedStore.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_ALREADY_LINKED", "이미 다른 점주에게 연결된 매장입니다.");
        }

        application.approve(adminUser, LocalDateTime.now());
        OwnerStoreLink link = null;
        if (verifiedStore != null) {
            link = ownerStoreLinkRepository.save(new OwnerStoreLink(
                application.getUser(),
                verifiedStore,
                application,
                OwnerStoreMatchStatus.MANUALLY_CONFIRMED,
                100,
                "business_verified_and_map_verified"
            ));
        }
        adminReviewLogRepository.save(new AdminReviewLog(
            application,
            adminUser,
            AdminReviewActionType.APPROVE,
            null,
            "{\"linkedStoreId\":" + (link == null ? "null" : link.getStore().getId()) + "}"
        ));

        return new OwnerApplicationReviewResponse(
            application.getId(),
            application.getUser().getId(),
            application.getReviewStatus().name(),
            application.getBusinessVerificationStatus().name(),
            application.getMapVerificationStatus().name(),
            verifiedStore == null ? null : verifiedStore.getId(),
            link == null ? null : link.getStore().getId(),
            application.getReviewedAt(),
            application.getRejectReason()
        );
    }

    @Transactional
    public OwnerApplicationReviewResponse reject(Long applicationId, String rejectReason, User adminUser) {
        assertAdmin(adminUser);
        OwnerApplication application = getApplication(applicationId);
        ensureReviewable(application);
        application.reject(adminUser, rejectReason.trim(), LocalDateTime.now());
        adminReviewLogRepository.save(new AdminReviewLog(
            application,
            adminUser,
            AdminReviewActionType.REJECT,
            rejectReason.trim(),
            null
        ));
        return new OwnerApplicationReviewResponse(
            application.getId(),
            application.getUser().getId(),
            application.getReviewStatus().name(),
            application.getBusinessVerificationStatus().name(),
            application.getMapVerificationStatus().name(),
            application.getVerifiedStore() == null ? null : application.getVerifiedStore().getId(),
            null,
            application.getReviewedAt(),
            application.getRejectReason()
        );
    }

    @Transactional(readOnly = true)
    public List<OwnerStoreLinkResponse> listOwnerStoreLinks(Long ownerUserId) {
        return ownerStoreLinkRepository.findAllByOwnerUserIdAndStoreDeletedAtIsNull(ownerUserId).stream()
            .map(this::toOwnerStoreLinkResponse)
            .toList();
    }

    @Transactional
    public OwnerStoreStatusResponse updateOwnerStoreStatus(User ownerUser, Long storeId, OwnerStoreStatusUpdateRequest request) {
        assertOwner(ownerUser);
        OwnerStoreLink link = ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(ownerUser.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STORE_ACCESS_DENIED", "해당 매장을 관리할 권한이 없습니다."));

        BusinessStatus status = parseBusinessStatus(request.status());
        storeService.updateStoreLiveStatus(link.getStore(), status, LiveStatusSource.OWNER_POS);

        return new OwnerStoreStatusResponse(
            link.getStore().getId(),
            link.getStore().getName(),
            link.getStore().getLiveBusinessStatus().name(),
            LiveStatusSource.OWNER_POS.name(),
            request.comment()
        );
    }

    @Transactional
    public OwnerLinkedStoreResponse updateOwnerStoreProfile(User ownerUser, Long storeId, OwnerStoreProfileUpdateRequest request) {
        assertOwner(ownerUser);
        OwnerStoreLink link = ownerStoreLinkRepository.findByOwnerUserIdAndStoreIdAndStoreDeletedAtIsNull(ownerUser.getId(), storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "STORE_ACCESS_DENIED", "해당 매장을 관리할 권한이 없습니다."));

        validateTimeField(request.openTime(), "영업 시작 시간");
        validateTimeField(request.closeTime(), "영업 종료 시간");
        validateTimeField(request.breakStart(), "브레이크 시작 시간");
        validateTimeField(request.breakEnd(), "브레이크 종료 시간");

        Store store = link.getStore();
        List<String> previousImageObjectKeys = deserializeImageObjectKeys(
            store.getOwnerImageObjectKeysJson() == null || store.getOwnerImageObjectKeysJson().isBlank()
                ? store.getOwnerImageUrlsJson()
                : store.getOwnerImageObjectKeysJson()
        );
        List<String> nextImageUrls = normalizeImageUrls(request.imageUrls());
        List<String> nextImageObjectKeys = normalizeImageObjectKeys(request.imageUrls());

        store.updateOwnerProfile(
            blankToNull(request.ownerNotice()),
            blankToNull(request.openTime()),
            blankToNull(request.closeTime()),
            blankToNull(request.breakStart()),
            blankToNull(request.breakEnd()),
            safeJson(nextImageUrls),
            safeJson(nextImageObjectKeys),
            null
        );

        List<String> removedImageObjectKeys = previousImageObjectKeys.stream()
            .filter(previousKey -> !nextImageObjectKeys.contains(previousKey))
            .toList();
        s3FileService.deleteFilesAfterCommit(removedImageObjectKeys);

        return toOwnerLinkedStoreResponse(link);
    }

    private OwnerApplication buildApplication(
        User ownerUser,
        OwnerApplicationRequest request,
        String storedKey,
        BusinessLicenseMetadata metadata
    ) {
        String normalizedAddress = request.businessAddress().trim();
        return new OwnerApplication(
            ownerUser,
            request.storeName().trim(),
            normalizeBusinessNumber(request.businessNumber()),
            request.representativeName().trim(),
            request.businessOpenDate(),
            normalizedAddress,
            addressNormalizer.normalize(normalizedAddress),
            normalizePhone(request.businessPhone()),
            storedKey,
            metadata.originalName(),
            metadata.originalFilename(),
            metadata.storedPath(),
            metadata.contentType(),
            metadata.size(),
            metadata.uploadedAt(),
            metadata.expiresAt()
        );
    }

    private BusinessLicenseMetadata requireBusinessLicenseMetadata(MultipartFile businessLicenseFile) {
        if (businessLicenseFile == null || businessLicenseFile.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "사업자등록증 파일은 필수입니다.");
        }

        String originalName = normalizeBusinessLicenseOriginalName(businessLicenseFile.getOriginalFilename());
        if (originalName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "사업자등록증 파일의 원본 파일명을 확인할 수 없습니다.");
        }

        String contentType = normalizeBusinessLicenseContentType(businessLicenseFile.getContentType());
        if (contentType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_CONTENT_TYPE", "사업자등록증 파일의 Content-Type을 확인할 수 없습니다.");
        }
        if (!BUSINESS_LICENSE_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "사업자등록증에 허용되지 않는 파일 형식입니다.");
        }

        long size = businessLicenseFile.getSize();
        if (size <= 0L) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_SIZE", "사업자등록증 파일 크기가 올바르지 않습니다.");
        }

        LocalDateTime uploadedAt = LocalDateTime.now();
        return new BusinessLicenseMetadata(
            originalName,
            originalName,
            "",
            "",
            contentType,
            size,
            uploadedAt,
            uploadedAt.plusDays(BUSINESS_LICENSE_RETENTION_DAYS)
        );
    }

    private String requireStoredBusinessLicenseKey(String storedKey) {
        String normalizedKey = storedKey == null ? "" : storedKey.trim();
        if (normalizedKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "파일 저장에 실패했습니다.");
        }
        return normalizedKey;
    }

    private void runInitialVerifications(OwnerApplication application) {
        runBusinessVerification(application);
        runMapVerification(application);
    }

    private void runBusinessVerification(OwnerApplication application) {
        application.markAutoVerificationPending();
        try {
            NationalTaxVerificationResult result = nationalTaxServiceClient.verifyBusiness(
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getBusinessOpenDate().format(NTS_DATE_FORMAT)
            );
            if (result.valid()) {
                application.markAutoVerified();
                businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
                    application,
                    BusinessVerificationType.AUTO_NTS,
                    VerificationRecordStatus.SUCCESS,
                    result.requestPayloadJson(),
                    result.responsePayloadJson(),
                    result.matchedBusinessNumber(),
                    result.matchedRepresentativeName(),
                    result.matchedOpenDate(),
                    result.matchedAddress(),
                    null,
                    null,
                    null,
                    LocalDateTime.now()
                ));
                return;
            }

            application.markAutoVerificationFailed();
            businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
                application,
                BusinessVerificationType.AUTO_NTS,
                VerificationRecordStatus.FAILED,
                result.requestPayloadJson(),
                result.responsePayloadJson(),
                result.matchedBusinessNumber(),
                result.matchedRepresentativeName(),
                result.matchedOpenDate(),
                result.matchedAddress(),
                result.failureCode(),
                result.failureMessage(),
                null,
                LocalDateTime.now()
            ));
        } catch (ApiException ex) {
            if (HttpStatus.SERVICE_UNAVAILABLE.equals(ex.getStatus())) {
                application.markAutoVerificationUnavailable();
            } else {
                application.markAutoVerificationFailed();
            }
            businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
                application,
                BusinessVerificationType.AUTO_NTS,
                VerificationRecordStatus.FAILED,
                null,
                null,
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getBusinessOpenDate().toString(),
                application.getBusinessAddressRaw(),
                ex.getCode(),
                ex.getMessage(),
                null,
                LocalDateTime.now()
            ));
        } catch (Exception ex) {
            application.markAutoVerificationFailed();
            businessVerificationHistoryRepository.save(new BusinessVerificationHistory(
                application,
                BusinessVerificationType.AUTO_NTS,
                VerificationRecordStatus.FAILED,
                null,
                null,
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getBusinessOpenDate().toString(),
                application.getBusinessAddressRaw(),
                "NATIONAL_TAX_API_ERROR",
                firstNonBlank(ex.getMessage(), "국세청 API 호출 중 예상치 못한 오류가 발생했습니다."),
                null,
                LocalDateTime.now()
            ));
        }
    }

    private void runMapVerification(OwnerApplication application) {
        application.markMapVerificationPending();
        String addressQuery = normalizeKakaoAddressQuery(application.getBusinessAddressRaw());
        log.info("Kakao address search query: {}", addressQuery);
        try {
            List<Candidate> candidates = searchCandidates(application, addressQuery);
            if (candidates.isEmpty()) {
                application.markMapVerificationFailed();
                mapVerificationHistoryRepository.save(new MapVerificationHistory(
                    application,
                    addressQuery,
                    MapVerificationQueryType.ADDRESS_ONLY,
                    VerificationRecordStatus.FAILED,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "[]",
                    null,
                    "KAKAO_ADDRESS_SEARCH_FAILED",
                    "카카오 주소 검색에 실패했습니다.",
                    LocalDateTime.now()
                ));
                return;
            }

            List<Candidate> narrowedCandidates = candidates.size() > 1
                ? narrowCandidatesByStoreName(application.getStoreName(), candidates)
                : candidates;

            if (narrowedCandidates.size() > 1) {
                Candidate ambiguousCandidate = narrowedCandidates.get(0);
                application.markMapVerificationFailed();
                mapVerificationHistoryRepository.save(new MapVerificationHistory(
                    application,
                    ambiguousCandidate.queryText(),
                    ambiguousCandidate.queryType(),
                    VerificationRecordStatus.FAILED,
                    narrowedCandidates.size(),
                    ambiguousCandidate.externalPlaceId(),
                    ambiguousCandidate.storeName(),
                    ambiguousCandidate.roadAddress(),
                    ambiguousCandidate.jibunAddress(),
                    ambiguousCandidate.phone(),
                    ambiguousCandidate.categoryName(),
                    ambiguousCandidate.latitude() == null ? null : ambiguousCandidate.latitude().toPlainString(),
                    ambiguousCandidate.longitude() == null ? null : ambiguousCandidate.longitude().toPlainString(),
                    safeJson(narrowedCandidates),
                    null,
                    "KAKAO_EXACT_ADDRESS_AMBIGUOUS",
                    "카카오 주소 검색 결과가 여러 개입니다. 상호명으로도 매장을 특정할 수 없습니다.",
                    LocalDateTime.now()
                ));
                return;
            }

            Candidate bestCandidate = narrowedCandidates.get(0);
            Optional<Store> existingStore = storeRepository.findByExternalSourceAndExternalPlaceIdAndDeletedAtIsNull(
                ExternalSource.KAKAO,
                bestCandidate.externalPlaceId()
            );
            if (existingStore.isPresent() && !isSameVerifiedStore(application, existingStore.get())) {
                application.markMapVerificationFailed();
                mapVerificationHistoryRepository.save(new MapVerificationHistory(
                    application,
                    bestCandidate.queryText(),
                    bestCandidate.queryType(),
                    VerificationRecordStatus.FAILED,
                    narrowedCandidates.size(),
                    bestCandidate.externalPlaceId(),
                    bestCandidate.storeName(),
                    bestCandidate.roadAddress(),
                    bestCandidate.jibunAddress(),
                    bestCandidate.phone(),
                    bestCandidate.categoryName(),
                    bestCandidate.latitude() == null ? null : bestCandidate.latitude().toPlainString(),
                    bestCandidate.longitude() == null ? null : bestCandidate.longitude().toPlainString(),
                    safeJson(narrowedCandidates),
                    existingStore.get(),
                    "KAKAO_PLACE_ALREADY_REGISTERED",
                    "이미 다른 점주가 등록한 매장입니다.",
                    LocalDateTime.now()
                ));
                return;
            }

            ResolveStoreResponse resolvedStore = storeService.resolveOrCreateStore(new ResolveStoreRequest(
                ExternalSource.KAKAO.name(),
                bestCandidate.externalPlaceId(),
                bestCandidate.storeName(),
                bestCandidate.storeAddress(),
                bestCandidate.phone(),
                bestCandidate.latitude(),
                bestCandidate.longitude()
            ));
            Store verifiedStore = storeService.getStore(resolvedStore.storeId());
            verifiedStore.markVerified(
                bestCandidate.roadAddress(),
                bestCandidate.jibunAddress(),
                bestCandidate.categoryName(),
                safeJson(bestCandidate.place()),
                LocalDateTime.now()
            );
            application.markMapVerified(verifiedStore);
            mapVerificationHistoryRepository.save(new MapVerificationHistory(
                application,
                bestCandidate.queryText(),
                bestCandidate.queryType(),
                VerificationRecordStatus.SUCCESS,
                narrowedCandidates.size(),
                bestCandidate.externalPlaceId(),
                bestCandidate.storeName(),
                bestCandidate.roadAddress(),
                bestCandidate.jibunAddress(),
                bestCandidate.phone(),
                bestCandidate.categoryName(),
                bestCandidate.latitude() == null ? null : bestCandidate.latitude().toPlainString(),
                bestCandidate.longitude() == null ? null : bestCandidate.longitude().toPlainString(),
                safeJson(narrowedCandidates),
                verifiedStore,
                null,
                null,
                LocalDateTime.now()
            ));
        } catch (ApiException ex) {
            application.markMapVerificationFailed();
            String failureCode = ex.getStatus() == HttpStatus.BAD_REQUEST
                ? "KAKAO_ADDRESS_SEARCH_FAILED"
                : ex.getCode();
            String failureMessage = ex.getStatus() == HttpStatus.BAD_REQUEST
                ? "카카오 주소 검색에 실패했습니다."
                : ex.getMessage();
            mapVerificationHistoryRepository.save(new MapVerificationHistory(
                application,
                addressQuery,
                MapVerificationQueryType.ADDRESS_ONLY,
                VerificationRecordStatus.FAILED,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                failureCode,
                failureMessage,
                LocalDateTime.now()
            ));
        } catch (Exception ex) {
            application.markMapVerificationFailed();
            mapVerificationHistoryRepository.save(new MapVerificationHistory(
                application,
                addressQuery,
                MapVerificationQueryType.ADDRESS_ONLY,
                VerificationRecordStatus.FAILED,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "KAKAO_ADDRESS_SEARCH_FAILED",
                firstNonBlank(ex.getMessage(), "카카오 주소 검색에 실패했습니다."),
                LocalDateTime.now()
            ));
        }
    }

    private List<Candidate> searchCandidates(OwnerApplication application, String addressQuery) {
        List<Candidate> candidates = new ArrayList<>();
        candidates.addAll(scoreCandidates(
            kakaoPlaceClient.searchByKeyword(addressQuery),
            application,
            addressQuery,
            MapVerificationQueryType.ADDRESS_ONLY,
            addressQuery
        ));
        return deduplicateCandidates(candidates);
    }

    private List<Candidate> narrowCandidatesByStoreName(String requestedStoreName, List<Candidate> candidates) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        String normalizedRequestedStoreName = normalizeStoreNameForMatch(requestedStoreName);
        if (normalizedRequestedStoreName.isBlank()) {
            return candidates;
        }

        List<Candidate> exactNameCandidates = candidates.stream()
            .filter(candidate -> normalizeStoreNameForMatch(candidate.storeName()).contains(normalizedRequestedStoreName))
            .toList();
        if (!exactNameCandidates.isEmpty()) {
            return exactNameCandidates;
        }

        return candidates;
    }

    private List<Candidate> scoreCandidates(
        List<KakaoKeywordSearchResponse.KakaoPlaceDocument> places,
        OwnerApplication application,
        String queryText,
        MapVerificationQueryType queryType,
        String requestedAddress
    ) {
        return places.stream()
            .map(place -> toExactMatchCandidate(application, place, queryText, queryType, requestedAddress))
            .filter(Candidate::addressExact)
            .sorted(Comparator.comparing(Candidate::phoneExact).reversed())
            .toList();
    }

    private Candidate toExactMatchCandidate(
        OwnerApplication application,
        KakaoKeywordSearchResponse.KakaoPlaceDocument place,
        String queryText,
        MapVerificationQueryType queryType,
        String requestedAddress
    ) {
        List<String> reasons = new ArrayList<>();
        String roadAddress = blankToNull(place.road_address_name());
        String jibunAddress = blankToNull(place.address_name());
        String preferredAddress = roadAddress != null ? roadAddress : blankToEmpty(jibunAddress);
        String normalizedRequestAddress = addressNormalizer.normalize(requestedAddress);
        String normalizedRoadAddress = addressNormalizer.normalize(roadAddress);
        String normalizedJibunAddress = addressNormalizer.normalize(jibunAddress);
        boolean addressExact = normalizedRequestAddress.equals(normalizedRoadAddress)
            || normalizedRequestAddress.equals(normalizedJibunAddress);
        if (addressExact) {
            reasons.add("address_exact");
        }

        String normalizedCandidatePhone = normalizePhone(place.phone());
        boolean phoneExact = !normalizedCandidatePhone.isBlank() && normalizedCandidatePhone.equals(application.getBusinessPhone());
        if (phoneExact) {
            reasons.add("phone_exact");
        }

        return new Candidate(
            place.id(),
            place.place_name(),
            preferredAddress,
            roadAddress,
            jibunAddress,
            place.phone(),
            place.category_name(),
            place.y(),
            place.x(),
            addressExact,
            phoneExact,
            reasons,
            queryText,
            queryType,
            place
        );
    }

    private OwnerApplication getApplication(Long applicationId) {
        return ownerApplicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OWNER_APPLICATION_NOT_FOUND", "점주 매장 신청을 찾을 수 없습니다."));
    }

    private String normalizeBusinessNumber(String businessNumber) {
        String digitsOnly = businessNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.length() != 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BUSINESS_NUMBER", "사업자 등록번호는 10자리여야 합니다.");
        }
        return digitsOnly;
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null) {
            return "";
        }
        return rawPhone.replaceAll("[^0-9]", "");
    }

    private String normalizeKakaoAddressQuery(String rawAddress) {
        if (rawAddress == null) {
            return "";
        }
        return rawAddress.trim().replaceAll("\\s+", " ");
    }

    private String normalizeStoreNameForMatch(String rawStoreName) {
        if (rawStoreName == null) {
            return "";
        }
        return rawStoreName
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^0-9a-z가-힣]", "");
    }

    private String normalizeBusinessLicenseOriginalName(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }

        String candidate = originalFilename.trim();
        if (candidate.isBlank()) {
            return "";
        }

        try {
            String filename = java.nio.file.Path.of(candidate).getFileName().toString().trim();
            return filename;
        } catch (Exception ex) {
            return candidate;
        }
    }

    private String normalizeBusinessLicenseContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSameVerifiedStore(OwnerApplication application, Store store) {
        return application.getVerifiedStore() != null
            && application.getVerifiedStore().getId().equals(store.getId());
    }

    private void ensureNoActiveDuplicateApplication(Long ownerUserId, String businessNumber, String businessAddress, Long currentApplicationId) {
        String normalizedBusinessNumber = normalizeBusinessNumber(businessNumber);
        String normalizedAddress = addressNormalizer.normalize(businessAddress);

        boolean exists = currentApplicationId == null
            ? ownerApplicationRepository.existsByUserIdAndBusinessNumberAndBusinessAddressNormalizedAndReviewStatusIn(
                ownerUserId,
                normalizedBusinessNumber,
                normalizedAddress,
                ACTIVE_REQUEST_STATUSES
            )
            : ownerApplicationRepository.existsByUserIdAndBusinessNumberAndBusinessAddressNormalizedAndReviewStatusInAndIdNot(
                ownerUserId,
                normalizedBusinessNumber,
                normalizedAddress,
                ACTIVE_REQUEST_STATUSES,
                currentApplicationId
            );

        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_APPLICATION_DUPLICATED", "같은 사업자번호와 실영업주소로 진행 중인 신청이 이미 있습니다.");
        }
    }

    private List<Candidate> deduplicateCandidates(List<Candidate> candidates) {
        java.util.Map<String, Candidate> byPlaceId = new java.util.LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            String key = candidate.externalPlaceId() == null || candidate.externalPlaceId().isBlank()
                ? candidate.storeName() + "|" + candidate.storeAddress()
                : candidate.externalPlaceId();
            Candidate existing = byPlaceId.get(key);
            if (existing == null) {
                byPlaceId.put(key, candidate);
            }
        }
        return List.copyOf(byPlaceId.values());
    }

    private BusinessStatus parseBusinessStatus(String rawStatus) {
        try {
            return BusinessStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_STATUS", "지원하지 않는 매장 상태입니다.");
        }
    }

    private void assertOwner(User user) {
        if (user.getRole() != UserRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "점주 계정만 매장 등록을 신청할 수 있습니다.");
        }
    }

    private void assertAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ONLY", "관리자만 처리할 수 있습니다.");
        }
    }

    private void ensureReviewable(OwnerApplication application) {
        if (application.getReviewStatus() == OwnerApplicationReviewStatus.APPROVED
            || application.getReviewStatus() == OwnerApplicationReviewStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_APPLICATION_ALREADY_REVIEWED", "이미 최종 처리된 신청입니다.");
        }
    }

    private OwnerApplicationResponse toApplicationResponse(OwnerApplication application) {
        return new OwnerApplicationResponse(
            application.getId(),
            application.getUser().getId(),
            application.getStoreName(),
            application.getReviewStatus().name(),
            application.getBusinessVerificationStatus().name(),
            application.getMapVerificationStatus().name(),
            application.getCreatedAt()
        );
    }

    private OwnerApplicationSummaryResponse toSummaryResponse(OwnerApplication application) {
        return new OwnerApplicationSummaryResponse(
            application.getId(),
            application.getUser().getId(),
            application.getUser().getEmail(),
            application.getUser().getNickname(),
            application.getStoreName(),
            application.getBusinessNumber(),
            application.getRepresentativeName(),
            application.getBusinessOpenDate(),
            application.getBusinessAddressRaw(),
            application.getBusinessPhone(),
            application.getBusinessLicenseObjectKey(),
            application.getDeletedAt(),
            application.getDeleteReason(),
            application.getReviewStatus().name(),
            application.getBusinessVerificationStatus().name(),
            application.getMapVerificationStatus().name(),
            application.getVerifiedStore() == null ? null : application.getVerifiedStore().getId(),
            application.getVerifiedStore() == null ? null : application.getVerifiedStore().getName(),
            application.getRejectReason(),
            application.getCreatedAt(),
            application.getReviewedAt()
        );
    }

    private OwnerStoreLinkResponse toOwnerStoreLinkResponse(OwnerStoreLink link) {
        return new OwnerStoreLinkResponse(
            link.getId(),
            link.getOwnerUser().getId(),
            link.getStore().getId(),
            link.getStore().getName(),
            link.getMatchStatus().name(),
            link.getMatchScore(),
            link.getMatchReason()
        );
    }

    private OwnerLinkedStoreResponse toOwnerLinkedStoreResponse(OwnerStoreLink link) {
        StoreEligibilityService.StoreEligibilitySnapshot eligibility = storeEligibilityService.describe(link.getStore(), true);
        return new OwnerLinkedStoreResponse(
            link.getId(),
            link.getOwnerUser().getId(),
            link.getOwnerUser().getOwnerDisplayName() == null ? link.getOwnerUser().getNickname() : link.getOwnerUser().getOwnerDisplayName(),
            link.getOwnerUser().getEmail(),
            link.getStore().getId(),
            link.getStore().getName(),
            link.getStore().getAddress(),
            link.getStore().getCategoryName(),
            link.getStore().getLiveBusinessStatus().name(),
            link.getStore().getOwnerNotice(),
            link.getStore().getOperatingOpenTime(),
            link.getStore().getOperatingCloseTime(),
            link.getStore().getBreakStartTime(),
            link.getStore().getBreakEndTime(),
            deserializeImageUrls(link.getStore().getOwnerImageUrlsJson()),
            eligibility.operationalState(),
            eligibility.closureRequestStatus(),
            eligibility.menuEligible(),
            eligibility.menuEditable(),
            eligibility.menuEligibilityReason()
        );
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void validateTimeField(String value, String label) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (!value.matches("^\\d{2}:\\d{2}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_FORMAT", label + " 형식이 올바르지 않습니다.");
        }
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        return ImageUrlMapper.toBrowserUrls(limitImageUrls(imageUrls));
    }

    private List<String> normalizeImageObjectKeys(List<String> imageUrls) {
        return ImageUrlMapper.toObjectKeys(limitImageUrls(imageUrls));
    }

    private List<String> deserializeImageUrls(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(
                rawJson,
                new TypeReference<List<String>>() {
                }
            );

            return parsed.stream()
                .map(ImageUrlMapper::toBrowserUrl)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<String> deserializeImageObjectKeys(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(
                rawJson,
                new TypeReference<List<String>>() {
                }
            );

            return parsed.stream()
                .map(ImageUrlMapper::toObjectKey)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<String> limitImageUrls(List<String> imageUrls) {
        return imageUrls == null ? List.of() : imageUrls.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .limit(10)
            .toList();
    }

    private record BusinessLicenseMetadata(
        String originalName,
        String originalFilename,
        String storedPath,
        String objectKey,
        String contentType,
        long size,
        LocalDateTime uploadedAt,
        LocalDateTime expiresAt
    ) {
        static BusinessLicenseMetadata from(OwnerApplication application) {
            LocalDateTime uploadedAt = application.getBusinessLicenseUploadedAt() == null
                ? LocalDateTime.now()
                : application.getBusinessLicenseUploadedAt();
            return new BusinessLicenseMetadata(
                normalizeFallbackOriginalName(application.getBusinessLicenseOriginalName(), application.getBusinessLicenseObjectKey()),
                normalizeFallbackOriginalName(application.getBusinessLicenseOriginalFilename(), application.getBusinessLicenseObjectKey()),
                normalizeFallbackStoredPath(application.getBusinessLicenseStoredPath(), application.getBusinessLicenseObjectKey()),
                normalizeFallbackStoredPath(application.getBusinessLicenseObjectKey(), application.getBusinessLicenseStoredPath()),
                normalizeFallbackContentType(application.getBusinessLicenseContentType()),
                application.getBusinessLicenseSize() == null || application.getBusinessLicenseSize() <= 0L
                    ? 0L
                    : application.getBusinessLicenseSize(),
                uploadedAt,
                application.getBusinessLicenseExpiresAt() == null
                    ? uploadedAt.plusDays(BUSINESS_LICENSE_RETENTION_DAYS)
                    : application.getBusinessLicenseExpiresAt()
            );
        }

        BusinessLicenseMetadata withStorage(String storedKey) {
            String normalizedStoredKey = normalizeFallbackStoredPath(storedPath, storedKey);
            String normalizedObjectKey = normalizeFallbackStoredPath(objectKey, storedKey);
            return new BusinessLicenseMetadata(
                originalName,
                originalFilename,
                normalizedStoredKey,
                normalizedObjectKey,
                contentType,
                size,
                uploadedAt,
                expiresAt
            );
        }

        private static String normalizeFallbackOriginalName(String originalName, String fallbackKey) {
            String candidate = originalName == null ? "" : originalName.trim();
            if (!candidate.isBlank()) {
                return candidate;
            }

            if (fallbackKey == null || fallbackKey.isBlank()) {
                return "";
            }

            String fallback = fallbackKey.trim();
            try {
                return java.nio.file.Path.of(fallback).getFileName().toString().trim();
            } catch (Exception ex) {
                return fallback;
            }
        }

        private static String normalizeFallbackStoredPath(String storedPath, String fallbackKey) {
            String candidate = storedPath == null ? "" : storedPath.trim();
            if (!candidate.isBlank()) {
                return candidate;
            }
            return fallbackKey == null ? "" : fallbackKey.trim();
        }

        private static String normalizeFallbackContentType(String contentType) {
            if (contentType == null || contentType.isBlank()) {
                return "application/octet-stream";
            }
            return contentType.trim().toLowerCase(Locale.ROOT);
        }
    }

    private record Candidate(
        String externalPlaceId,
        String storeName,
        String storeAddress,
        String roadAddress,
        String jibunAddress,
        String phone,
        String categoryName,
        java.math.BigDecimal latitude,
        java.math.BigDecimal longitude,
        boolean addressExact,
        boolean phoneExact,
        List<String> reasons,
        String queryText,
        MapVerificationQueryType queryType,
        KakaoKeywordSearchResponse.KakaoPlaceDocument place
    ) {
    }

}

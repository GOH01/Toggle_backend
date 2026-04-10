package com.toggle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.toggle.repository.AdminReviewLogRepository;
import com.toggle.repository.BusinessVerificationHistoryRepository;
import com.toggle.repository.MapVerificationHistoryRepository;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OwnerApplicationService {

    private static final DateTimeFormatter NTS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OwnerApplicationRepository ownerApplicationRepository;
    private final OwnerStoreLinkRepository ownerStoreLinkRepository;
    private final BusinessVerificationHistoryRepository businessVerificationHistoryRepository;
    private final MapVerificationHistoryRepository mapVerificationHistoryRepository;
    private final AdminReviewLogRepository adminReviewLogRepository;
    private final OwnerDocumentStorageService ownerDocumentStorageService;
    private final AddressNormalizer addressNormalizer;
    private final StoreService storeService;
    private final KakaoPlaceClient kakaoPlaceClient;
    private final NationalTaxServiceClient nationalTaxServiceClient;
    private final ObjectMapper objectMapper;

    public OwnerApplicationService(
        OwnerApplicationRepository ownerApplicationRepository,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        BusinessVerificationHistoryRepository businessVerificationHistoryRepository,
        MapVerificationHistoryRepository mapVerificationHistoryRepository,
        AdminReviewLogRepository adminReviewLogRepository,
        OwnerDocumentStorageService ownerDocumentStorageService,
        AddressNormalizer addressNormalizer,
        StoreService storeService,
        KakaoPlaceClient kakaoPlaceClient,
        NationalTaxServiceClient nationalTaxServiceClient,
        ObjectMapper objectMapper
    ) {
        this.ownerApplicationRepository = ownerApplicationRepository;
        this.ownerStoreLinkRepository = ownerStoreLinkRepository;
        this.businessVerificationHistoryRepository = businessVerificationHistoryRepository;
        this.mapVerificationHistoryRepository = mapVerificationHistoryRepository;
        this.adminReviewLogRepository = adminReviewLogRepository;
        this.ownerDocumentStorageService = ownerDocumentStorageService;
        this.addressNormalizer = addressNormalizer;
        this.storeService = storeService;
        this.kakaoPlaceClient = kakaoPlaceClient;
        this.nationalTaxServiceClient = nationalTaxServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OwnerApplicationResponse createApplication(User ownerUser, OwnerApplicationRequest request, MultipartFile businessLicenseFile) {
        assertOwner(ownerUser);

        OwnerDocumentStorageService.StoredOwnerDocument storedDocument = ownerDocumentStorageService.store(businessLicenseFile);
        OwnerApplication application = buildApplication(ownerUser, request, storedDocument);
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

        OwnerDocumentStorageService.StoredOwnerDocument storedDocument = businessLicenseFile == null
            ? new OwnerDocumentStorageService.StoredOwnerDocument(
                application.getBusinessLicenseStoredPath(),
                application.getBusinessLicenseOriginalName(),
                application.getBusinessLicenseContentType()
            )
            : ownerDocumentStorageService.store(businessLicenseFile);

        String normalizedAddress = request.businessAddress().trim();
        application.updateOwnerDraft(
            request.storeName().trim(),
            normalizeBusinessNumber(request.businessNumber()),
            request.representativeName().trim(),
            request.businessOpenDate(),
            normalizedAddress,
            addressNormalizer.normalize(normalizedAddress),
            normalizePhone(request.businessPhone()),
            storedDocument.storedPath(),
            storedDocument.originalName(),
            storedDocument.contentType()
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
                    history.getSelectedPlaceName(),
                    history.getSelectedRoadAddress(),
                    history.getSelectedExternalPlaceId(),
                    history.getFailureMessage(),
                    history.getVerifiedAt()
                ))
                .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<OwnerLinkedStoreResponse> listLinkedStores(Long ownerUserId) {
        return ownerStoreLinkRepository.findAllByOwnerUserId(ownerUserId).stream()
            .map(link -> new OwnerLinkedStoreResponse(
                link.getId(),
                link.getStore().getId(),
                link.getStore().getName(),
                link.getStore().getAddress(),
                link.getStore().getLiveBusinessStatus().name()
            ))
            .toList();
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

        if (!application.isApprovalReady()) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_REGISTRATION_NOT_APPROVABLE", "사업자 검증과 카카오맵 검증이 모두 완료되어야 승인할 수 있습니다.");
        }
        if (ownerStoreLinkRepository.existsByStoreId(application.getVerifiedStore().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_ALREADY_LINKED", "이미 다른 점주에게 연결된 매장입니다.");
        }

        application.approve(adminUser, LocalDateTime.now());
        OwnerStoreLink link = ownerStoreLinkRepository.save(new OwnerStoreLink(
            application.getUser(),
            application.getVerifiedStore(),
            application,
            OwnerStoreMatchStatus.MANUALLY_CONFIRMED,
            100,
            "business_verified_and_map_verified"
        ));
        adminReviewLogRepository.save(new AdminReviewLog(
            application,
            adminUser,
            AdminReviewActionType.APPROVE,
            null,
            "{\"linkedStoreId\":" + link.getStore().getId() + "}"
        ));

        return new OwnerApplicationReviewResponse(
            application.getId(),
            application.getUser().getId(),
            application.getReviewStatus().name(),
            application.getBusinessVerificationStatus().name(),
            application.getMapVerificationStatus().name(),
            application.getVerifiedStore().getId(),
            link.getStore().getId(),
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
        return ownerStoreLinkRepository.findAllByOwnerUserId(ownerUserId).stream()
            .map(this::toOwnerStoreLinkResponse)
            .toList();
    }

    @Transactional
    public OwnerStoreStatusResponse updateOwnerStoreStatus(User ownerUser, Long storeId, OwnerStoreStatusUpdateRequest request) {
        assertOwner(ownerUser);
        OwnerStoreLink link = ownerStoreLinkRepository.findByOwnerUserIdAndStoreId(ownerUser.getId(), storeId)
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

    private OwnerApplication buildApplication(
        User ownerUser,
        OwnerApplicationRequest request,
        OwnerDocumentStorageService.StoredOwnerDocument storedDocument
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
            storedDocument.storedPath(),
            storedDocument.originalName(),
            storedDocument.contentType()
        );
    }

    private void runInitialVerifications(OwnerApplication application) {
        runBusinessVerification(application);
        runMapVerification(application);
    }

    private void runBusinessVerification(OwnerApplication application) {
        application.markAutoVerificationPending();
        NationalTaxVerificationResult result;
        try {
            result = nationalTaxServiceClient.verifyBusiness(
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getBusinessOpenDate().format(NTS_DATE_FORMAT),
                application.getBusinessAddressRaw()
            );
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
            return;
        }

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
    }

    private void runMapVerification(OwnerApplication application) {
        application.markMapVerificationPending();
        List<Candidate> candidates;
        try {
            candidates = searchCandidates(application);
        } catch (ApiException ex) {
            application.markMapVerificationFailed();
            mapVerificationHistoryRepository.save(new MapVerificationHistory(
                application,
                application.getStoreName() + " " + application.getBusinessAddressRaw(),
                MapVerificationQueryType.NAME_AND_ADDRESS,
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
                ex.getCode(),
                ex.getMessage(),
                LocalDateTime.now()
            ));
            return;
        }
        if (candidates.isEmpty()) {
            application.markMapVerificationFailed();
            mapVerificationHistoryRepository.save(new MapVerificationHistory(
                application,
                application.getStoreName() + " " + application.getBusinessAddressRaw(),
                MapVerificationQueryType.NAME_AND_ADDRESS,
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
                "KAKAO_PLACE_NOT_FOUND",
                "카카오맵에서 실영업주소가 정확히 일치하는 매장을 찾지 못했습니다.",
                LocalDateTime.now()
            ));
            return;
        }

        if (candidates.size() > 1) {
            Candidate ambiguousCandidate = candidates.get(0);
            application.markMapVerificationFailed();
            mapVerificationHistoryRepository.save(new MapVerificationHistory(
                application,
                ambiguousCandidate.queryText(),
                ambiguousCandidate.queryType(),
                VerificationRecordStatus.FAILED,
                candidates.size(),
                ambiguousCandidate.externalPlaceId(),
                ambiguousCandidate.storeName(),
                ambiguousCandidate.roadAddress(),
                ambiguousCandidate.jibunAddress(),
                ambiguousCandidate.phone(),
                ambiguousCandidate.categoryName(),
                ambiguousCandidate.latitude() == null ? null : ambiguousCandidate.latitude().toPlainString(),
                ambiguousCandidate.longitude() == null ? null : ambiguousCandidate.longitude().toPlainString(),
                safeJson(candidates),
                null,
                "KAKAO_EXACT_ADDRESS_AMBIGUOUS",
                "실영업주소가 정확히 일치하는 카카오 매장이 여러 개라 자동 확정할 수 없습니다.",
                LocalDateTime.now()
            ));
            return;
        }

        Candidate bestCandidate = candidates.get(0);

        ResolveStoreResponse resolvedStore = storeService.resolveStore(new ResolveStoreRequest(
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
            candidates.size(),
            bestCandidate.externalPlaceId(),
            bestCandidate.storeName(),
            bestCandidate.roadAddress(),
            bestCandidate.jibunAddress(),
            bestCandidate.phone(),
            bestCandidate.categoryName(),
            bestCandidate.latitude() == null ? null : bestCandidate.latitude().toPlainString(),
            bestCandidate.longitude() == null ? null : bestCandidate.longitude().toPlainString(),
            safeJson(candidates),
            verifiedStore,
            null,
            null,
            LocalDateTime.now()
        ));
    }

    private List<Candidate> searchCandidates(OwnerApplication application) {
        List<Candidate> candidates = new ArrayList<>();
        String addressQuery = application.getBusinessAddressRaw();
        String nameAndAddressQuery = application.getStoreName() + " " + addressQuery;
        candidates.addAll(scoreCandidates(
            kakaoPlaceClient.searchByKeyword(nameAndAddressQuery),
            application,
            nameAndAddressQuery,
            MapVerificationQueryType.NAME_AND_ADDRESS,
            addressQuery
        ));

        String addressOnlyQuery = addressQuery;
        candidates.addAll(scoreCandidates(
            kakaoPlaceClient.searchByKeyword(addressOnlyQuery),
            application,
            addressOnlyQuery,
            MapVerificationQueryType.ADDRESS_ONLY,
            addressQuery
        ));
        return deduplicateCandidates(candidates);
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
            application.getBusinessLicenseOriginalName(),
            application.getBusinessLicenseContentType(),
            application.getBusinessLicenseStoredPath(),
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

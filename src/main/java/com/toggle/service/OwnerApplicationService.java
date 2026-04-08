package com.toggle.service;

import com.toggle.dto.owner.OwnerApplicationRequest;
import com.toggle.dto.owner.OwnerApplicationResponse;
import com.toggle.dto.owner.OwnerApplicationReviewResponse;
import com.toggle.dto.owner.OwnerApplicationSummaryResponse;
import com.toggle.dto.owner.OwnerLinkedStoreResponse;
import com.toggle.dto.owner.OwnerStoreLinkResponse;
import com.toggle.dto.owner.OwnerStoreMatchCandidateResponse;
import com.toggle.dto.owner.OwnerStoreStatusResponse;
import com.toggle.dto.owner.OwnerStoreStatusUpdateRequest;
import com.toggle.entity.BusinessStatus;
import com.toggle.entity.OwnerApplication;
import com.toggle.entity.OwnerApplicationReviewStatus;
import com.toggle.entity.OwnerStoreLink;
import com.toggle.entity.OwnerStoreMatchStatus;
import com.toggle.entity.LiveStatusSource;
import com.toggle.entity.Store;
import com.toggle.entity.User;
import com.toggle.entity.UserRole;
import com.toggle.global.exception.ApiException;
import com.toggle.repository.OwnerApplicationRepository;
import com.toggle.repository.OwnerStoreLinkRepository;
import com.toggle.repository.StoreRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OwnerApplicationService {

    private final OwnerApplicationRepository ownerApplicationRepository;
    private final OwnerStoreLinkRepository ownerStoreLinkRepository;
    private final StoreRepository storeRepository;
    private final OwnerDocumentStorageService ownerDocumentStorageService;
    private final AddressNormalizer addressNormalizer;
    private final StoreService storeService;

    public OwnerApplicationService(
        OwnerApplicationRepository ownerApplicationRepository,
        OwnerStoreLinkRepository ownerStoreLinkRepository,
        StoreRepository storeRepository,
        OwnerDocumentStorageService ownerDocumentStorageService,
        AddressNormalizer addressNormalizer,
        StoreService storeService
    ) {
        this.ownerApplicationRepository = ownerApplicationRepository;
        this.ownerStoreLinkRepository = ownerStoreLinkRepository;
        this.storeRepository = storeRepository;
        this.ownerDocumentStorageService = ownerDocumentStorageService;
        this.addressNormalizer = addressNormalizer;
        this.storeService = storeService;
    }

    @Transactional
    public OwnerApplicationResponse createApplication(User ownerUser, OwnerApplicationRequest request, MultipartFile businessLicenseFile) {
        assertOwner(ownerUser);

        String normalizedBusinessName = request.businessName().trim();
        String normalizedBusinessNumber = normalizeBusinessNumber(request.businessNumber());
        String normalizedBusinessAddress = request.businessAddress().trim();
        String normalizedBusinessAddressKey = addressNormalizer.normalize(normalizedBusinessAddress);

        OwnerDocumentStorageService.StoredOwnerDocument storedDocument = ownerDocumentStorageService.store(businessLicenseFile);

        OwnerApplication application = ownerApplicationRepository.save(new OwnerApplication(
            ownerUser,
            normalizedBusinessName,
            normalizedBusinessNumber,
            normalizedBusinessAddress,
            normalizedBusinessAddressKey,
            storedDocument.storedPath(),
            storedDocument.originalName(),
            storedDocument.contentType(),
            OwnerApplicationReviewStatus.PENDING
        ));

        return new OwnerApplicationResponse(
            application.getId(),
            ownerUser.getId(),
            application.getBusinessName(),
            application.getReviewStatus().name(),
            application.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<OwnerApplicationSummaryResponse> listMyApplications(Long ownerUserId) {
        return ownerApplicationRepository.findAllByUserIdOrderByCreatedAtDesc(ownerUserId)
            .stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerApplicationSummaryResponse> listApplications() {
        return ownerApplicationRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerLinkedStoreResponse> listLinkedStores(Long ownerUserId) {
        return ownerStoreLinkRepository.findAllByOwnerUserId(ownerUserId)
            .stream()
            .map(link -> new OwnerLinkedStoreResponse(
                link.getId(),
                link.getStore().getId(),
                link.getStore().getName(),
                link.getStore().getAddress(),
                link.getStore().getLiveBusinessStatus().name()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerStoreMatchCandidateResponse> findMatchCandidates(Long applicationId) {
        OwnerApplication application = getApplication(applicationId);
        List<Store> candidates = storeRepository.findTop10ByAddressNormalizedContaining(application.getBusinessAddressNormalized());

        return candidates.stream()
            .map(store -> {
                List<String> reasons = new ArrayList<>();
                int score = scoreCandidate(application, store, reasons);
                return new OwnerStoreMatchCandidateResponse(
                    store.getId(),
                    store.getName(),
                    store.getAddress(),
                    score,
                    reasons
                );
            })
            .sorted((a, b) -> Integer.compare(b.score(), a.score()))
            .toList();
    }

    @Transactional
    public OwnerApplicationReviewResponse approve(Long applicationId, Long storeId) {
        OwnerApplication application = getApplication(applicationId);
        Store store = storeRepository.findById(storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", "매장을 찾을 수 없습니다."));

        if (ownerStoreLinkRepository.existsByStoreId(storeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_ALREADY_LINKED", "이미 다른 점주와 연결된 매장입니다.");
        }

        List<String> reasons = new ArrayList<>();
        int score = scoreCandidate(application, store, reasons);

        ownerStoreLinkRepository.save(new OwnerStoreLink(
            application.getUser(),
            store,
            application,
            OwnerStoreMatchStatus.MANUALLY_CONFIRMED,
            score,
            String.join(",", reasons)
        ));

        LocalDateTime reviewedAt = LocalDateTime.now();
        application.approve(reviewedAt);

        return new OwnerApplicationReviewResponse(
            application.getId(),
            application.getUser().getId(),
            application.getReviewStatus().name(),
            store.getId(),
            application.getReviewedAt(),
            application.getRejectReason()
        );
    }

    @Transactional
    public OwnerApplicationReviewResponse reject(Long applicationId, String rejectReason) {
        OwnerApplication application = getApplication(applicationId);
        LocalDateTime reviewedAt = LocalDateTime.now();

        application.reject(rejectReason.trim(), reviewedAt);

        return new OwnerApplicationReviewResponse(
            application.getId(),
            application.getUser().getId(),
            application.getReviewStatus().name(),
            null,
            application.getReviewedAt(),
            application.getRejectReason()
        );
    }

    @Transactional(readOnly = true)
    public List<OwnerStoreLinkResponse> listOwnerStoreLinks(Long ownerUserId) {
        return ownerStoreLinkRepository.findAllByOwnerUserId(ownerUserId)
            .stream()
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

    private OwnerApplication getApplication(Long applicationId) {
        return ownerApplicationRepository.findById(applicationId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OWNER_APPLICATION_NOT_FOUND", "점주 매장 신청을 찾을 수 없습니다."));
    }

    private int scoreCandidate(OwnerApplication application, Store store, List<String> reasons) {
        int score = 0;

        if (application.getBusinessAddressNormalized().equals(store.getAddressNormalized())) {
            score += 70;
            reasons.add("address_exact");
        } else if (store.getAddressNormalized().contains(application.getBusinessAddressNormalized())
            || application.getBusinessAddressNormalized().contains(store.getAddressNormalized())) {
            score += 50;
            reasons.add("address_partial");
        }

        String normalizedStoreName = store.getName().trim().toLowerCase(Locale.ROOT);
        String normalizedBusinessName = application.getBusinessName().trim().toLowerCase(Locale.ROOT);
        if (normalizedStoreName.contains(normalizedBusinessName) || normalizedBusinessName.contains(normalizedStoreName)) {
            score += 20;
            reasons.add("name_similar");
        }

        return score;
    }

    private String normalizeBusinessNumber(String businessNumber) {
        String digitsOnly = businessNumber.replaceAll("[^0-9]", "");
        if (digitsOnly.length() != 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BUSINESS_NUMBER", "사업자 등록번호는 10자리여야 합니다.");
        }
        return digitsOnly;
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

    private OwnerApplicationSummaryResponse toSummaryResponse(OwnerApplication application) {
        return new OwnerApplicationSummaryResponse(
            application.getId(),
            application.getUser().getId(),
            application.getUser().getEmail(),
            application.getUser().getNickname(),
            application.getBusinessName(),
            application.getBusinessNumber(),
            application.getBusinessAddressRaw(),
            application.getBusinessLicenseOriginalName(),
            application.getBusinessLicenseContentType(),
            application.getBusinessLicenseStoredPath(),
            application.getReviewStatus().name(),
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
}

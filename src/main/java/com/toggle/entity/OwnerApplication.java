package com.toggle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "owner_applications")
public class OwnerApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String storeName;

    @Column(nullable = false, length = 20)
    private String businessNumber;

    @Column(nullable = false)
    private String representativeName;

    @Column(nullable = false)
    private LocalDate businessOpenDate;

    @Column(nullable = false)
    private String businessAddressRaw;

    @Column(nullable = false)
    private String businessAddressNormalized;

    @Column(nullable = false, length = 30)
    private String businessPhone;

    @Column(nullable = false)
    private String businessLicenseObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnerApplicationReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessVerificationStatus businessVerificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MapVerificationStatus mapVerificationStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_store_id")
    private Store verifiedStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_reviewed_by_admin_id")
    private User finalReviewedByAdmin;

    private LocalDateTime reviewedAt;

    private LocalDateTime deletedAt;

    @Column(length = 500)
    private String deleteReason;

    @Column(length = 500)
    private String rejectReason;

    protected OwnerApplication() {
    }

    public OwnerApplication(
        User user,
        String storeName,
        String businessNumber,
        String representativeName,
        LocalDate businessOpenDate,
        String businessAddressRaw,
        String businessAddressNormalized,
        String businessPhone,
        String businessLicenseObjectKey
    ) {
        this.user = user;
        this.storeName = storeName;
        this.businessNumber = businessNumber;
        this.representativeName = representativeName;
        this.businessOpenDate = businessOpenDate;
        this.businessAddressRaw = businessAddressRaw;
        this.businessAddressNormalized = businessAddressNormalized;
        this.businessPhone = businessPhone;
        this.businessLicenseObjectKey = businessLicenseObjectKey;
        this.reviewStatus = OwnerApplicationReviewStatus.PENDING;
        this.businessVerificationStatus = BusinessVerificationStatus.NOT_STARTED;
        this.mapVerificationStatus = MapVerificationStatus.NOT_STARTED;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getBusinessNumber() {
        return businessNumber;
    }

    public String getRepresentativeName() {
        return representativeName;
    }

    public LocalDate getBusinessOpenDate() {
        return businessOpenDate;
    }

    public String getBusinessAddressRaw() {
        return businessAddressRaw;
    }

    public String getBusinessAddressNormalized() {
        return businessAddressNormalized;
    }

    public String getBusinessPhone() {
        return businessPhone;
    }

    public String getBusinessLicenseObjectKey() {
        return businessLicenseObjectKey;
    }

    public OwnerApplicationReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public BusinessVerificationStatus getBusinessVerificationStatus() {
        return businessVerificationStatus;
    }

    public MapVerificationStatus getMapVerificationStatus() {
        return mapVerificationStatus;
    }

    public Store getVerifiedStore() {
        return verifiedStore;
    }

    public User getFinalReviewedByAdmin() {
        return finalReviewedByAdmin;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void updateOwnerDraft(
        String storeName,
        String businessNumber,
        String representativeName,
        LocalDate businessOpenDate,
        String businessAddressRaw,
        String businessAddressNormalized,
        String businessPhone,
        String businessLicenseObjectKey
    ) {
        this.storeName = storeName;
        this.businessNumber = businessNumber;
        this.representativeName = representativeName;
        this.businessOpenDate = businessOpenDate;
        this.businessAddressRaw = businessAddressRaw;
        this.businessAddressNormalized = businessAddressNormalized;
        this.businessPhone = businessPhone;
        this.businessLicenseObjectKey = businessLicenseObjectKey;
        resetVerification();
    }

    public void moveToUnderReview() {
        if (this.reviewStatus == OwnerApplicationReviewStatus.PENDING) {
            this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
        }
    }

    public void markAutoVerificationPending() {
        this.businessVerificationStatus = BusinessVerificationStatus.AUTO_VERIFICATION_PENDING;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markAutoVerified() {
        this.businessVerificationStatus = BusinessVerificationStatus.AUTO_VERIFIED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markAutoVerificationUnavailable() {
        this.businessVerificationStatus = BusinessVerificationStatus.AUTO_VERIFICATION_UNAVAILABLE;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markAutoVerificationFailed() {
        this.businessVerificationStatus = BusinessVerificationStatus.AUTO_VERIFICATION_FAILED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markManualVerificationRequired() {
        this.businessVerificationStatus = BusinessVerificationStatus.MANUAL_VERIFICATION_REQUIRED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markManualVerified() {
        this.businessVerificationStatus = BusinessVerificationStatus.MANUAL_VERIFIED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markManualVerificationFailed() {
        this.businessVerificationStatus = BusinessVerificationStatus.MANUAL_VERIFICATION_FAILED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markMapVerificationPending() {
        this.mapVerificationStatus = MapVerificationStatus.SEARCH_PENDING;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markMapVerified(Store verifiedStore) {
        this.verifiedStore = verifiedStore;
        this.mapVerificationStatus = MapVerificationStatus.VERIFIED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void markMapVerificationFailed() {
        this.verifiedStore = null;
        this.mapVerificationStatus = MapVerificationStatus.FAILED;
        this.reviewStatus = OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public void approve(User admin, LocalDateTime reviewedAt) {
        this.reviewStatus = OwnerApplicationReviewStatus.APPROVED;
        this.finalReviewedByAdmin = admin;
        this.reviewedAt = reviewedAt;
        this.rejectReason = null;
    }

    public void reject(User admin, String rejectReason, LocalDateTime reviewedAt) {
        this.reviewStatus = OwnerApplicationReviewStatus.REJECTED;
        this.finalReviewedByAdmin = admin;
        this.reviewedAt = reviewedAt;
        this.rejectReason = rejectReason;
    }

    public void markBusinessLicenseDeleted(LocalDateTime deletedAt, String deleteReason) {
        this.deletedAt = deletedAt;
        this.deleteReason = deleteReason;
    }

    public boolean isApprovalReady() {
        boolean businessVerified = this.businessVerificationStatus == BusinessVerificationStatus.AUTO_VERIFIED
            || this.businessVerificationStatus == BusinessVerificationStatus.MANUAL_VERIFIED;
        return businessVerified && this.mapVerificationStatus == MapVerificationStatus.VERIFIED && this.verifiedStore != null;
    }

    public boolean isEditableByOwner() {
        return this.reviewStatus == OwnerApplicationReviewStatus.PENDING || this.reviewStatus == OwnerApplicationReviewStatus.UNDER_REVIEW;
    }

    public boolean isBusinessLicenseDeleted() {
        return this.deletedAt != null;
    }

    private void resetVerification() {
        this.reviewStatus = OwnerApplicationReviewStatus.PENDING;
        this.businessVerificationStatus = BusinessVerificationStatus.NOT_STARTED;
        this.mapVerificationStatus = MapVerificationStatus.NOT_STARTED;
        this.verifiedStore = null;
        this.finalReviewedByAdmin = null;
        this.reviewedAt = null;
        this.rejectReason = null;
    }
}

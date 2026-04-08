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
    private String businessName;

    @Column(nullable = false, length = 20)
    private String businessNumber;

    @Column(nullable = false)
    private String businessAddressRaw;

    @Column(nullable = false)
    private String businessAddressNormalized;

    @Column(nullable = false)
    private String businessLicenseStoredPath;

    @Column(nullable = false)
    private String businessLicenseOriginalName;

    @Column(nullable = false)
    private String businessLicenseContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnerApplicationReviewStatus reviewStatus;

    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String rejectReason;

    protected OwnerApplication() {
    }

    public OwnerApplication(
        User user,
        String businessName,
        String businessNumber,
        String businessAddressRaw,
        String businessAddressNormalized,
        String businessLicenseStoredPath,
        String businessLicenseOriginalName,
        String businessLicenseContentType,
        OwnerApplicationReviewStatus reviewStatus
    ) {
        this.user = user;
        this.businessName = businessName;
        this.businessNumber = businessNumber;
        this.businessAddressRaw = businessAddressRaw;
        this.businessAddressNormalized = businessAddressNormalized;
        this.businessLicenseStoredPath = businessLicenseStoredPath;
        this.businessLicenseOriginalName = businessLicenseOriginalName;
        this.businessLicenseContentType = businessLicenseContentType;
        this.reviewStatus = reviewStatus;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getBusinessNumber() {
        return businessNumber;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessLicenseStoredPath() {
        return businessLicenseStoredPath;
    }

    public String getBusinessAddressRaw() {
        return businessAddressRaw;
    }

    public String getBusinessAddressNormalized() {
        return businessAddressNormalized;
    }

    public String getBusinessLicenseOriginalName() {
        return businessLicenseOriginalName;
    }

    public String getBusinessLicenseContentType() {
        return businessLicenseContentType;
    }

    public OwnerApplicationReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void approve(LocalDateTime reviewedAt) {
        this.reviewStatus = OwnerApplicationReviewStatus.APPROVED;
        this.reviewedAt = reviewedAt;
        this.rejectReason = null;
    }

    public void reject(String rejectReason, LocalDateTime reviewedAt) {
        this.reviewStatus = OwnerApplicationReviewStatus.REJECTED;
        this.reviewedAt = reviewedAt;
        this.rejectReason = rejectReason;
    }
}

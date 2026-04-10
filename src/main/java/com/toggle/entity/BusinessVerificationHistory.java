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
@Table(name = "business_verification_histories")
public class BusinessVerificationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private OwnerApplication request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessVerificationType verificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationRecordStatus status;

    @Column(columnDefinition = "TEXT")
    private String requestPayloadJson;

    @Column(columnDefinition = "TEXT")
    private String responsePayloadJson;

    private String matchedBusinessNumber;

    private String matchedRepresentativeName;

    private String matchedOpenDate;

    private String matchedAddress;

    private String failureCode;

    @Column(length = 1000)
    private String failureMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_admin_id")
    private User verifiedByAdmin;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    protected BusinessVerificationHistory() {
    }

    public BusinessVerificationHistory(
        OwnerApplication request,
        BusinessVerificationType verificationType,
        VerificationRecordStatus status,
        String requestPayloadJson,
        String responsePayloadJson,
        String matchedBusinessNumber,
        String matchedRepresentativeName,
        String matchedOpenDate,
        String matchedAddress,
        String failureCode,
        String failureMessage,
        User verifiedByAdmin,
        LocalDateTime verifiedAt
    ) {
        this.request = request;
        this.verificationType = verificationType;
        this.status = status;
        this.requestPayloadJson = requestPayloadJson;
        this.responsePayloadJson = responsePayloadJson;
        this.matchedBusinessNumber = matchedBusinessNumber;
        this.matchedRepresentativeName = matchedRepresentativeName;
        this.matchedOpenDate = matchedOpenDate;
        this.matchedAddress = matchedAddress;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.verifiedByAdmin = verifiedByAdmin;
        this.verifiedAt = verifiedAt;
    }

    public Long getId() {
        return id;
    }

    public BusinessVerificationType getVerificationType() {
        return verificationType;
    }

    public VerificationRecordStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}

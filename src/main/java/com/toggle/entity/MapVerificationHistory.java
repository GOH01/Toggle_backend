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
@Table(name = "map_verification_histories")
public class MapVerificationHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private OwnerApplication request;

    @Column(nullable = false)
    private String queryText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MapVerificationQueryType queryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationRecordStatus status;

    @Column(nullable = false)
    private int candidateCount;

    private String selectedExternalPlaceId;

    private String selectedPlaceName;

    private String selectedRoadAddress;

    private String selectedJibunAddress;

    private String selectedPhone;

    private String selectedCategoryName;

    private String selectedLatitude;

    private String selectedLongitude;

    @Column(columnDefinition = "TEXT")
    private String responsePayloadJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_store_id")
    private Store linkedStore;

    private String failureCode;

    @Column(length = 1000)
    private String failureMessage;

    @Column(nullable = false)
    private LocalDateTime verifiedAt;

    protected MapVerificationHistory() {
    }

    public MapVerificationHistory(
        OwnerApplication request,
        String queryText,
        MapVerificationQueryType queryType,
        VerificationRecordStatus status,
        int candidateCount,
        String selectedExternalPlaceId,
        String selectedPlaceName,
        String selectedRoadAddress,
        String selectedJibunAddress,
        String selectedPhone,
        String selectedCategoryName,
        String selectedLatitude,
        String selectedLongitude,
        String responsePayloadJson,
        Store linkedStore,
        String failureCode,
        String failureMessage,
        LocalDateTime verifiedAt
    ) {
        this.request = request;
        this.queryText = queryText;
        this.queryType = queryType;
        this.status = status;
        this.candidateCount = candidateCount;
        this.selectedExternalPlaceId = selectedExternalPlaceId;
        this.selectedPlaceName = selectedPlaceName;
        this.selectedRoadAddress = selectedRoadAddress;
        this.selectedJibunAddress = selectedJibunAddress;
        this.selectedPhone = selectedPhone;
        this.selectedCategoryName = selectedCategoryName;
        this.selectedLatitude = selectedLatitude;
        this.selectedLongitude = selectedLongitude;
        this.responsePayloadJson = responsePayloadJson;
        this.linkedStore = linkedStore;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.verifiedAt = verifiedAt;
    }

    public Long getId() {
        return id;
    }

    public String getQueryText() {
        return queryText;
    }

    public MapVerificationQueryType getQueryType() {
        return queryType;
    }

    public VerificationRecordStatus getStatus() {
        return status;
    }

    public String getSelectedPlaceName() {
        return selectedPlaceName;
    }

    public String getSelectedRoadAddress() {
        return selectedRoadAddress;
    }

    public String getSelectedJibunAddress() {
        return selectedJibunAddress;
    }

    public String getSelectedExternalPlaceId() {
        return selectedExternalPlaceId;
    }

    public String getSelectedPhone() {
        return selectedPhone;
    }

    public String getSelectedCategoryName() {
        return selectedCategoryName;
    }

    public String getSelectedLatitude() {
        return selectedLatitude;
    }

    public String getSelectedLongitude() {
        return selectedLongitude;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }
}

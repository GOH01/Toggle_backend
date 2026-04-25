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

@Entity
@Table(name = "store_closure_requests")
public class StoreClosureRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(length = 1000)
    private String requestReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreClosureRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private java.time.LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewReason;

    protected StoreClosureRequest() {
    }

    public StoreClosureRequest(Store store, User ownerUser, String requestReason) {
        this.store = store;
        this.ownerUser = ownerUser;
        this.requestReason = requestReason;
        this.status = StoreClosureRequestStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public String getRequestReason() {
        return requestReason;
    }

    public StoreClosureRequestStatus getStatus() {
        return status;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public java.time.LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void approve(User reviewedBy, String reviewReason) {
        this.status = StoreClosureRequestStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = java.time.LocalDateTime.now();
        this.reviewReason = reviewReason;
    }

    public void reject(User reviewedBy, String reviewReason) {
        this.status = StoreClosureRequestStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = java.time.LocalDateTime.now();
        this.reviewReason = reviewReason;
    }
}

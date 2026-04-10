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
@Table(name = "admin_review_logs")
public class AdminReviewLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private OwnerApplication request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminReviewActionType actionType;

    @Column(length = 1000)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    protected AdminReviewLog() {
    }

    public AdminReviewLog(
        OwnerApplication request,
        User adminUser,
        AdminReviewActionType actionType,
        String reason,
        String metadataJson
    ) {
        this.request = request;
        this.adminUser = adminUser;
        this.actionType = actionType;
        this.reason = reason;
        this.metadataJson = metadataJson;
    }
}

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "owner_store_links",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_owner_store_links_store", columnNames = {"store_id"})
    }
)
public class OwnerStoreLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private OwnerApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnerStoreMatchStatus matchStatus;

    @Column(nullable = false)
    private int matchScore;

    @Column(length = 1000)
    private String matchReason;

    protected OwnerStoreLink() {
    }

    public OwnerStoreLink(
        User ownerUser,
        Store store,
        OwnerApplication application,
        OwnerStoreMatchStatus matchStatus,
        int matchScore,
        String matchReason
    ) {
        this.ownerUser = ownerUser;
        this.store = store;
        this.application = application;
        this.matchStatus = matchStatus;
        this.matchScore = matchScore;
        this.matchReason = matchReason;
    }

    public Long getId() {
        return id;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public Store getStore() {
        return store;
    }

    public OwnerApplication getApplication() {
        return application;
    }

    public OwnerStoreMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public String getMatchReason() {
        return matchReason;
    }
}

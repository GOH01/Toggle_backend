package com.toggle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "maps",
    indexes = {
        @Index(name = "idx_maps_owner_deleted_created", columnList = "owner_user_id, deleted_at, created_at")
    }
)
public class UserMap extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_map_uuid", nullable = false, unique = true, length = 36)
    private String publicMapUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User ownerUser;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "profile_image_url", length = 100000)
    private String profileImageUrl;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryMap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    private LocalDateTime deletedAt;

    protected UserMap() {
    }

    public UserMap(User ownerUser, String publicMapUuid, String title, String description, String profileImageUrl, boolean isPublic, boolean primaryMap) {
        this.ownerUser = ownerUser;
        this.publicMapUuid = publicMapUuid;
        this.title = title;
        this.description = description;
        this.profileImageUrl = profileImageUrl;
        this.isPublic = isPublic;
        this.primaryMap = primaryMap;
    }

    public Long getId() {
        return id;
    }

    public String getPublicMapUuid() {
        return publicMapUuid;
    }

    public User getOwnerUser() {
        return ownerUser;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public boolean isPrimary() {
        return primaryMap;
    }

    public User getDeletedBy() {
        return deletedBy;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void update(Boolean isPublic, String title, String description, String profileImageUrl) {
        if (isPublic != null) {
            this.isPublic = isPublic;
        }
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public void markPrimary(boolean primary) {
        this.primaryMap = primary;
    }

    public void markDeleted(User deletedBy, LocalDateTime deletedAt) {
        this.deletedBy = deletedBy;
        this.deletedAt = deletedAt;
    }
}

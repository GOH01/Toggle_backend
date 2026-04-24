package com.toggle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String nickname;

    @Column(name = "public_map_uuid", unique = true, length = 36)
    private String publicMapUuid;

    @Column(name = "owner_display_name")
    private String ownerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "is_public_map", nullable = false)
    private boolean publicMap;

    @Column(name = "map_title")
    private String mapTitle;

    @Column(name = "map_description", length = 1000)
    private String mapDescription;

    @Column(name = "profile_image_url", length = 100000)
    private String profileImageUrl;

    protected User() {
    }

    public User(String email, String password, String nickname, String ownerDisplayName, UserRole role, UserStatus status) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.ownerDisplayName = ownerDisplayName;
        this.role = role;
        this.status = status;
        ensurePublicMapUuid();
    }

    public User(String email, String password, String nickname, UserRole role, UserStatus status) {
        this(email, password, nickname, null, role, status);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public String getPublicMapUuid() {
        return publicMapUuid;
    }

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isPublicMap() {
        return publicMap;
    }

    public String getMapTitle() {
        return mapTitle;
    }

    public String getMapDescription() {
        return mapDescription;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void updateMapProfile(Boolean publicMap, String mapTitle, String mapDescription, String profileImageUrl) {
        if (publicMap != null) {
            this.publicMap = publicMap;
        }
        if (mapTitle != null) {
            this.mapTitle = mapTitle;
        }
        if (mapDescription != null) {
            this.mapDescription = mapDescription;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public boolean ensurePublicMapUuid() {
        if (publicMapUuid != null && !publicMapUuid.isBlank()) {
            return false;
        }

        this.publicMapUuid = UUID.randomUUID().toString();
        return true;
    }

    @PrePersist
    void prePersist() {
        ensurePublicMapUuid();
    }
}

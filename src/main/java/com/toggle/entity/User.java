package com.toggle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    @Column(name = "owner_display_name")
    private String ownerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

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

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String password) {
        this.password = password;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }
}

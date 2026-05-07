package com.toggle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "public_map_likes",
    uniqueConstraints = @UniqueConstraint(name = "uk_public_map_likes_map_user", columnNames = {"map_id", "user_id"})
)
public class UserMapLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private UserMap map;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected UserMapLike() {
    }

    public UserMapLike(UserMap map, User user) {
        this.map = map;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public UserMap getMap() {
        return map;
    }

    public User getUser() {
        return user;
    }
}

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

@Entity
@Table(
    name = "store_reviews",
    indexes = {
        @Index(name = "idx_store_reviews_store_created_at", columnList = "store_id, created_at"),
        @Index(name = "idx_store_reviews_store_user_created_at", columnList = "store_id, user_id, created_at"),
        @Index(name = "idx_store_reviews_user_created_at", columnList = "user_id, created_at")
    }
)
public class StoreReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String imageUrlsJson;

    protected StoreReview() {
    }

    public StoreReview(User user, Store store, int rating, String content) {
        this(user, store, rating, content, null);
    }

    public StoreReview(User user, Store store, int rating, String content, String imageUrlsJson) {
        this.user = user;
        this.store = store;
        this.rating = rating;
        this.content = content;
        this.imageUrlsJson = imageUrlsJson;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Store getStore() {
        return store;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public String getImageUrlsJson() {
        return imageUrlsJson;
    }

    public void updateReview(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    public void updateReview(int rating, String content, String imageUrlsJson) {
        this.rating = rating;
        this.content = content;
        this.imageUrlsJson = imageUrlsJson;
    }
}

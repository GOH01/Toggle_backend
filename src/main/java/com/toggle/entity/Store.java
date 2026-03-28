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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "stores",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stores_external_source_place", columnNames = {"external_source", "external_place_id"})
    }
)
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalSource externalSource;

    @Column(nullable = false)
    private String externalPlaceId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessStatus businessStatus;

    @Column(nullable = false)
    private boolean isVerified;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Store() {
    }

    public Store(
        ExternalSource externalSource,
        String externalPlaceId,
        String name,
        String phone,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        this.externalSource = externalSource;
        this.externalPlaceId = externalPlaceId;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.businessStatus = BusinessStatus.CLOSED;
        this.isVerified = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ExternalSource getExternalSource() {
        return externalSource;
    }

    public String getExternalPlaceId() {
        return externalPlaceId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BusinessStatus getBusinessStatus() {
        return businessStatus;
    }
}

package com.toggle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "public_institutions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_public_institutions_external", columnNames = {"external_source", "external_place_id"})
    }
)
public class PublicInstitution extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalSource externalSource;

    @Column(nullable = false)
    private String externalPlaceId;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CongestionLevel congestionLevel;

    @Column
    private Integer waitTime;

    @Column
    private String operatingHours;

    private LocalDateTime statusUpdatedAt;

    protected PublicInstitution() {
    }

    public PublicInstitution(
        ExternalSource externalSource,
        String externalPlaceId,
        String name
    ) {
        this.externalSource = externalSource;
        this.externalPlaceId = externalPlaceId;
        this.name = name;
        this.congestionLevel = CongestionLevel.UNKNOWN;
        this.statusUpdatedAt = LocalDateTime.now();
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

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public CongestionLevel getCongestionLevel() {
        return congestionLevel;
    }

    public Integer getWaitTime() {
        return waitTime;
    }

    public String getOperatingHours() {
        return operatingHours;
    }

    public LocalDateTime getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void updateStatus(CongestionLevel congestionLevel, Integer waitTime) {
        this.congestionLevel = congestionLevel;
        this.waitTime = waitTime;
        this.statusUpdatedAt = LocalDateTime.now();
    }

    public void updateMetadata(String name, String address, BigDecimal latitude, BigDecimal longitude, String operatingHours) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.operatingHours = operatingHours;
    }
}

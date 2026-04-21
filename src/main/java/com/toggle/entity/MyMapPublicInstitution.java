package com.toggle.entity;

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
    name = "my_map_public_institutions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_my_map_publics_user_pi", columnNames = {"user_id", "public_institution_id"})
    }
)
public class MyMapPublicInstitution extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "public_institution_id", nullable = false)
    private PublicInstitution publicInstitution;

    protected MyMapPublicInstitution() {
    }

    public MyMapPublicInstitution(User user, PublicInstitution publicInstitution) {
        this.user = user;
        this.publicInstitution = publicInstitution;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public PublicInstitution getPublicInstitution() {
        return publicInstitution;
    }
}

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
    name = "my_map_stores",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_my_map_stores_map_store", columnNames = {"map_id", "store_id"})
    }
)
public class MyMapStore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private UserMap map;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    protected MyMapStore() {
    }

    public MyMapStore(User user, Store store) {
        this.user = user;
        this.store = store;
    }

    public MyMapStore(User user, UserMap map, Store store) {
        this.user = user;
        this.map = map;
        this.store = store;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public UserMap getMap() {
        return map;
    }

    public void setMap(UserMap map) {
        this.map = map;
    }

    public Store getStore() {
        return store;
    }
}

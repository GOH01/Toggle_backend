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

@Entity
@Table(name = "store_menus")
public class StoreMenu extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private boolean representative;

    @Column(length = 1000)
    private String description;

    @Column(length = 100000)
    private String imageUrl;

    @Column(length = 1024)
    private String imageObjectKey;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean available;

    protected StoreMenu() {
    }

    public StoreMenu(
        Store store,
        String name,
        int price,
        boolean representative,
        String description,
        String imageUrl,
        String imageObjectKey,
        int displayOrder,
        boolean available
    ) {
        this.store = store;
        this.name = name;
        this.price = price;
        this.representative = representative;
        this.description = description;
        this.imageUrl = imageUrl;
        this.imageObjectKey = imageObjectKey;
        this.displayOrder = displayOrder;
        this.available = available;
    }

    public StoreMenu(
        Store store,
        String name,
        int price,
        boolean representative,
        String description,
        String imageUrl,
        int displayOrder,
        boolean available
    ) {
        this(store, name, price, representative, description, imageUrl, null, displayOrder, available);
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public boolean isRepresentative() {
        return representative;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getImageObjectKey() {
        return imageObjectKey;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isAvailable() {
        return available;
    }
}

package com.example.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "sanctions_list")
public class SanctionsList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String normalizedName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private String source; // e.g. OFAC, EU, UN, Local Authority

    @Column(nullable = false)
    private int riskScore = 50;
    // No updates: insert-only by design for audit purposes
    public Long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getNormalizedName() {
        return normalizedName;
    }
    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }
    public LocalDateTime getAddedAt() {
        return addedAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public String getSource() {
        return source;
    }
    public int getRiskScore() {
        return riskScore;
    }
}

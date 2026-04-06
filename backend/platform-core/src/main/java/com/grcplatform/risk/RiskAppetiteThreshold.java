package com.grcplatform.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Risk appetite ceiling per org and optional category. Uses a versioned active record pattern —
 * effective_to NULL = currently active.
 */
@Entity
@Table(name = "risk_appetite_thresholds")
@Getter
public class RiskAppetiteThreshold {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "category")
    private String category;

    @Column(name = "threshold_score", nullable = false)
    private int thresholdScore;

    @Column(name = "critical_multiplier", nullable = false)
    private BigDecimal criticalMultiplier = new BigDecimal("1.5");

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "notes")
    private String notes;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (effectiveFrom == null) effectiveFrom = LocalDate.now();
    }

    public static RiskAppetiteThreshold create(UUID orgId, String category, int thresholdScore,
            UUID createdBy, String notes) {
        var t = new RiskAppetiteThreshold();
        t.orgId = orgId;
        t.category = category;
        t.thresholdScore = thresholdScore;
        t.createdBy = createdBy;
        t.notes = notes;
        return t;
    }

    public void setCriticalMultiplier(BigDecimal criticalMultiplier) {
        this.criticalMultiplier = criticalMultiplier;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}

package com.grcplatform.core.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "control_effectiveness_scores")
public class ControlEffectiveness {

    @Id
    @Column(name = "control_record_id", updatable = false, nullable = false)
    private UUID controlRecordId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "effectiveness_score", nullable = false)
    private int effectivenessScore;

    @Column(name = "effectiveness_rating", nullable = false, length = 30)
    private String effectivenessRating;

    @Column(name = "test_count_12m", nullable = false)
    private int testCount12m;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public UUID getControlRecordId() {
        return controlRecordId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public int getEffectivenessScore() {
        return effectivenessScore;
    }

    public String getEffectivenessRating() {
        return effectivenessRating;
    }

    public int getTestCount12m() {
        return testCount12m;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public static ControlEffectiveness compute(UUID controlRecordId, UUID orgId, int score,
            String rating, int testCount12m) {
        var e = new ControlEffectiveness();
        e.controlRecordId = controlRecordId;
        e.orgId = orgId;
        e.effectivenessScore = score;
        e.effectivenessRating = rating;
        e.testCount12m = testCount12m;
        e.computedAt = Instant.now();
        return e;
    }

    public static ControlEffectiveness notAssessed(UUID controlRecordId, UUID orgId) {
        return compute(controlRecordId, orgId, 0, "Not Assessed", 0);
    }
}

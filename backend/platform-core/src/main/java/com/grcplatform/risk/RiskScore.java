package com.grcplatform.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Computed risk score snapshot for a risk record. PK is the record_id — one row per risk record.
 */
@Entity
@Table(name = "risk_scores")
@Getter
public class RiskScore {

    @Id
    @Column(name = "record_id", updatable = false, nullable = false)
    private UUID recordId;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "likelihood_score", nullable = false)
    private BigDecimal likelihoodScore;

    @Column(name = "impact_score", nullable = false)
    private BigDecimal impactScore;

    @Column(name = "inherent_score", nullable = false)
    private BigDecimal inherentScore;

    @Column(name = "inherent_rating", nullable = false)
    private String inherentRating;

    @Column(name = "residual_likelihood")
    private BigDecimal residualLikelihood;

    @Column(name = "residual_impact")
    private BigDecimal residualImpact;

    @Column(name = "residual_score")
    private BigDecimal residualScore;

    @Column(name = "residual_rating")
    private String residualRating;

    @Column(name = "appetite_alignment")
    private String appetiteAlignment;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @PrePersist
    protected void prePersist() {
        if (computedAt == null) computedAt = Instant.now();
    }

    public static RiskScore compute(UUID recordId, UUID orgId, BigDecimal likelihood,
            BigDecimal impact) {
        var rs = new RiskScore();
        rs.recordId = recordId;
        rs.orgId = orgId;
        rs.likelihoodScore = likelihood;
        rs.impactScore = impact;
        rs.inherentScore = likelihood.multiply(impact);
        rs.inherentRating = computeRating(rs.inherentScore);
        return rs;
    }

    /** Updates residual scores after control effectiveness is known. */
    public void applyResidual(BigDecimal residualLikelihood, BigDecimal residualImpact,
            int appetiteThreshold) {
        this.residualLikelihood = residualLikelihood;
        this.residualImpact = residualImpact;
        this.residualScore = residualLikelihood.multiply(residualImpact);
        this.residualRating = computeRating(this.residualScore);
        this.appetiteAlignment = residualScore.intValue() <= appetiteThreshold ? "within_appetite"
                : "above_appetite";
        this.computedAt = Instant.now();
    }

    private static String computeRating(BigDecimal score) {
        int s = score.intValue();
        if (s >= 20) return "Critical";
        if (s >= 12) return "High";
        if (s >= 6) return "Medium";
        return "Low";
    }

    /** Projects this entity to its API DTO. */
    public RiskScoreDto toDto() {
        return new RiskScoreDto(recordId, likelihoodScore, impactScore, inherentScore,
                inherentRating, residualScore, residualRating, appetiteAlignment, computedAt);
    }
}

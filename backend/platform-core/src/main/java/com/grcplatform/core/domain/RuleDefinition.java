package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rule_definitions")
public class RuleDefinition extends BaseEntity {

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @Column(name = "trigger_event")
    private String triggerEvent;

    @Column(name = "target_field")
    private String targetField;

    @Column(name = "rule_dsl", nullable = false)
    private String ruleDsl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "config_version", nullable = false)
    private int configVersion = 1;

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getName() {
        return name;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getTriggerEvent() {
        return triggerEvent;
    }

    public String getTargetField() {
        return targetField;
    }

    public String getRuleDsl() {
        return ruleDsl;
    }

    public boolean isActive() {
        return active;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRuleDsl(String ruleDsl) {
        this.ruleDsl = ruleDsl;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setTriggerEvent(String triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public static RuleDefinition create(UUID orgId, UUID applicationId, String name,
            String ruleType, String ruleDsl) {
        RuleDefinition rd = new RuleDefinition();
        rd.setOrgId(orgId);
        rd.applicationId = applicationId;
        rd.name = name;
        rd.ruleType = ruleType;
        rd.ruleDsl = ruleDsl;
        return rd;
    }
}

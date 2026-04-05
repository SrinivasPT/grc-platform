package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class Application extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "internal_key", nullable = false)
    private String internalKey;

    @Column(name = "record_prefix", nullable = false)
    private String recordPrefix;

    @Column(name = "description")
    private String description;

    @Column(name = "icon")
    private String icon;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "config_version", nullable = false)
    private int configVersion = 1;

    @Column(name = "config")
    private String config;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public String getName() {
        return name;
    }

    public String getInternalKey() {
        return internalKey;
    }

    public String getRecordPrefix() {
        return recordPrefix;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isActive() {
        return active;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public String getConfig() {
        return config;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInternalKey(String internalKey) {
        this.internalKey = internalKey;
    }

    public void setRecordPrefix(String recordPrefix) {
        this.recordPrefix = recordPrefix;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public static Application create(UUID orgId, String name, String internalKey,
            String recordPrefix, UUID createdBy) {
        Application app = new Application();
        app.setOrgId(orgId);
        app.name = name;
        app.internalKey = internalKey;
        app.recordPrefix = recordPrefix;
        app.createdBy = createdBy;
        return app;
    }
}

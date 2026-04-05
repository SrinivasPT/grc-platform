package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "field_definitions")
public class FieldDefinition extends BaseEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "internal_key", nullable = false, updatable = false)
    private String internalKey;

    @Column(name = "field_type", nullable = false, updatable = false)
    private String fieldType;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @Column(name = "is_searchable", nullable = false)
    private boolean searchable = true;

    @Column(name = "materialize_as_column", nullable = false)
    private boolean materializeAsColumn = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "config")
    private String config;

    @Column(name = "validation_rules")
    private String validationRules;

    @Column(name = "config_version", nullable = false)
    private int configVersion = 1;

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getName() {
        return name;
    }

    public String getInternalKey() {
        return internalKey;
    }

    public String getFieldType() {
        return fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public boolean isMaterializeAsColumn() {
        return materializeAsColumn;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getConfig() {
        return config;
    }

    public String getValidationRules() {
        return validationRules;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setSearchable(boolean searchable) {
        this.searchable = searchable;
    }

    public void setMaterializeAsColumn(boolean materializeAsColumn) {
        this.materializeAsColumn = materializeAsColumn;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public void setValidationRules(String validationRules) {
        this.validationRules = validationRules;
    }

    public static FieldDefinition create(UUID orgId, UUID applicationId, String name,
            String internalKey, String fieldType) {
        FieldDefinition fd = new FieldDefinition();
        fd.setOrgId(orgId);
        fd.applicationId = applicationId;
        fd.name = name;
        fd.internalKey = internalKey;
        fd.fieldType = fieldType;
        return fd;
    }
}

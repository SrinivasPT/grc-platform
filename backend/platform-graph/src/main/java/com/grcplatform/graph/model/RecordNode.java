package com.grcplatform.graph.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j node representing a GRC record. Carries the labels: [:GrcRecord] + [:AppKey] (dynamic,
 * applied via Cypher MERGE).
 *
 * Only key/indexed properties are projected — not all field values. Full details are always fetched
 * from SQL Server.
 */
@Node("GrcRecord")
public class RecordNode {

    @Id
    @GeneratedValue
    private Long neo4jId;

    @Property("id")
    private String id;

    @Property("orgId")
    private String orgId;

    @Property("appKey")
    private String appKey;

    @Property("displayName")
    private String displayName;

    @Property("status")
    private String status;

    @Property("workflowState")
    private String workflowState;

    @Property("updatedAt")
    private Instant updatedAt;

    public RecordNode() {}

    public static RecordNode of(UUID id, UUID orgId, String appKey, String displayName,
            String status, String workflowState, Instant updatedAt) {
        RecordNode n = new RecordNode();
        n.id = id.toString();
        n.orgId = orgId.toString();
        n.appKey = appKey;
        n.displayName = displayName;
        n.status = status;
        n.workflowState = workflowState;
        n.updatedAt = updatedAt;
        return n;
    }

    public String getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public String getWorkflowState() {
        return workflowState;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setWorkflowState(String workflowState) {
        this.workflowState = workflowState;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

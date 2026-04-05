package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_history")
public class WorkflowHistory {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "instance_id", nullable = false, updatable = false)
    private UUID instanceId;

    @Column(name = "from_state", nullable = false, updatable = false)
    private String fromState;

    @Column(name = "to_state", nullable = false, updatable = false)
    private String toState;

    @Column(name = "transition_key", nullable = false, updatable = false)
    private String transitionKey;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "comment")
    private String comment;

    @Column(name = "transitioned_at", nullable = false, updatable = false)
    private Instant transitionedAt;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        transitionedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getInstanceId() { return instanceId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getTransitionKey() { return transitionKey; }
    public UUID getActorId() { return actorId; }
    public String getComment() { return comment; }
    public Instant getTransitionedAt() { return transitionedAt; }

    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public void setInstanceId(UUID instanceId) { this.instanceId = instanceId; }
    public void setFromState(String fromState) { this.fromState = fromState; }
    public void setToState(String toState) { this.toState = toState; }
    public void setTransitionKey(String transitionKey) { this.transitionKey = transitionKey; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public void setComment(String comment) { this.comment = comment; }
}

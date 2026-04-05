package com.grcplatform.core.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "in_app_notifications")
public class InAppNotification {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "link_url")
    private String linkUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getRecipientId() { return recipientId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLinkUrl() { return linkUrl; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }

    public static InAppNotification create(UUID orgId, UUID recipientId, String title, String body,
            String linkUrl) {
        InAppNotification n = new InAppNotification();
        n.orgId = orgId;
        n.recipientId = recipientId;
        n.title = title;
        n.body = body;
        n.linkUrl = linkUrl;
        return n;
    }

    public void markRead() { this.read = true; }
}

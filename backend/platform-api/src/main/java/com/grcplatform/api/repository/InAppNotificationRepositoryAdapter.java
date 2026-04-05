package com.grcplatform.api.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.grcplatform.core.domain.InAppNotification;
import com.grcplatform.core.repository.InAppNotificationRepository;

@Repository
public class InAppNotificationRepositoryAdapter implements InAppNotificationRepository {

    private final SpringInAppNotificationRepository jpa;

    public InAppNotificationRepositoryAdapter(SpringInAppNotificationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public InAppNotification save(InAppNotification notification) {
        return jpa.save(notification);
    }

    @Override
    public List<InAppNotification> findUnreadByRecipient(UUID orgId, UUID recipientId) {
        return jpa.findUnreadByOrgIdAndRecipientId(orgId, recipientId);
    }

    @Override
    public void markRead(UUID id) {
        jpa.markReadById(id);
    }
}

package com.grcplatform.core.repository;

import com.grcplatform.core.domain.InAppNotification;

import java.util.List;
import java.util.UUID;

public interface InAppNotificationRepository {

    InAppNotification save(InAppNotification notification);

    List<InAppNotification> findUnreadByRecipient(UUID orgId, UUID recipientId);

    void markRead(UUID id);
}

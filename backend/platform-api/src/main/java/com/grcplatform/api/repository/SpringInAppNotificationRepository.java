package com.grcplatform.api.repository;

import com.grcplatform.core.domain.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringInAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    @Query("SELECT n FROM InAppNotification n WHERE n.orgId = :orgId AND n.recipientId = :recipientId AND n.read = false ORDER BY n.createdAt DESC")
    List<InAppNotification> findUnreadByOrgIdAndRecipientId(@Param("orgId") UUID orgId,
            @Param("recipientId") UUID recipientId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.read = true WHERE n.id = :id")
    void markReadById(@Param("id") UUID id);
}

package com.jia.notification.api.dto;


import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;

import java.time.LocalDateTime;

// 단건 상태 조회 + 목록 조회에 공통으로 사용.
// Worker 구현 이후에도 이 DTO는 변경 없이 status/retryCount 등이 자연스럽게 노출됨.
public record NotificationResponse(
        Long id,
        Long recipientId,
        String eventType,
        Long referenceId,
        String channel,
        String title,
        String content,
        NotificationStatus status,
        int retryCount,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getRecipientId(),
                n.getEventType(),
                n.getReferenceId(),
                n.getChannel(),
                n.getTitle(),
                n.getContent(),
                n.getStatus(),
                n.getRetryCount(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt(),
                n.getUpdatedAt()
        );
    }
}


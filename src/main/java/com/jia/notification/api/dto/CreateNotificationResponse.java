package com.jia.notification.api.dto;


import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;

// 알림 생성 요청 수락 응답.
// "즉시 발송"이 아닌 "요청 접수"임을 명확히 하기 위해 별도 DTO로 분리.
// 클라이언트는 이 ID로 이후 상태 조회 가능.
public record CreateNotificationResponse(
        Long notificationId,
        NotificationStatus status  // 항상 PENDING
) {
    public static CreateNotificationResponse from(Notification notification) {
        return new CreateNotificationResponse(
                notification.getId(),
                notification.getStatus()
        );
    }
}


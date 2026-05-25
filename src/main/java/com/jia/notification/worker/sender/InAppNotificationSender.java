package com.jia.notification.worker.sender;

import com.jia.notification.domain.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InAppNotificationSender implements NotificationSender {

    @Override
    public String supportedChannel() {
        return "IN_APP";
    }

    @Override
    public void send(Notification notification) {
        log.info("[IN_APP] 발송 처리. notificationId={}, recipientId={}, title={}",
                notification.getId(), notification.getRecipientId(), notification.getTitle());

        // TODO: 실제 인앱 알림 연동 시 여기에 구현 (ex. FCM, WebSocket push 등)
    }
}

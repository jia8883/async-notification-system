package com.jia.notification.worker.sender;


import com.jia.notification.domain.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationSender implements NotificationSender {

    @Override
    public String supportedChannel() {
        return "EMAIL";
    }

    /**
     * Mock 구현. 실제 Email Provider 연동 시 이 메서드만 교체.
     * 예외 발생 시 Worker의 retry 정책에 따라 처리됨 (현재는 단순 로그).
     */
    @Override
    public void send(Notification notification) {
        log.info("[EMAIL] 발송 처리. notificationId={}, recipientId={}, title={}",
                notification.getId(), notification.getRecipientId(), notification.getTitle());

        // TODO: 실제 Email Provider 연동 시 여기에 구현
        // emailClient.send(EmailMessage.builder()
        //     .to(userEmail)
        //     .subject(notification.getTitle())
        //     .body(notification.getContent())
        //     .build());
    }
}


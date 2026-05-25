package com.jia.notification.worker.sender;

import com.jia.notification.domain.Notification;

/**
 * 채널별 알림 발송 인터페이스.
 *
 * Worker가 채널 타입을 직접 if/switch로 분기하지 않도록 최소한의 추상화만 적용.
 * 실제 Email Provider 연동 시 EmailNotificationSender 구현체만 교체하면 됨.
 */
public interface NotificationSender {

    String supportedChannel();

    void send(Notification notification);
}

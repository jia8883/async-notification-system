package com.jia.notification.api.exception;

// DB unique constraint 위반 시 사용.
// DataIntegrityViolationException을 그대로 클라이언트에 노출하지 않기 위해 변환.
public class DuplicateNotificationException extends RuntimeException {

    public DuplicateNotificationException(
            String eventType, Long referenceId, Long recipientId, String channel
    ) {
        super(String.format(
                "이미 동일한 알림이 존재합니다. eventType=%s, referenceId=%d, recipientId=%d, channel=%s",
                eventType, referenceId, recipientId, channel
        ));
    }
}


package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.repository.NotificationRepository;
import com.jia.notification.worker.sender.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 알림 단건 처리 — 트랜잭션 경계.
 *
 * NotificationPollingWorker와 분리한 이유:
 * Spring @Transactional은 프록시 기반으로 동작하기 때문에,
 * 같은 클래스 내 self-invocation 호출 시 프록시를 타지 않아 트랜잭션이 무시됨.
 * processOne()을 별도 Bean으로 분리해 이 문제를 회피.
 */
@Slf4j
@Component
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final Map<String, NotificationSender> senderMap;

    public NotificationProcessor(
            NotificationRepository notificationRepository,
            List<NotificationSender> senders
    ) {
        this.notificationRepository = notificationRepository;
        // channel 문자열 → sender 매핑. 새 채널 추가 시 구현체만 추가하면 자동 등록.
        this.senderMap = senders.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationSender::supportedChannel,
                        Function.identity()
                ));
    }

    /**
     * 트랜잭션 범위: PROCESSING 전이 → 발송 시도 → SUCCESS/FAILED 반영.
     * 1건 실패해도 다른 건의 트랜잭션에 영향 없음.
     */
    @Transactional
    public void process(Notification notification) {
        // 1. PROCESSING 전이 — Worker가 처리 중임을 표시
        notification.startProcessing();
        notificationRepository.save(notification);

        // 2. 채널에 맞는 Sender 조회
        NotificationSender sender = senderMap.get(notification.getChannel());
        if (sender == null) {
            // 설정 오류. 재시도해도 의미 없으므로 즉시 FAILED.
            log.error("지원하지 않는 채널. notificationId={}, channel={}",
                    notification.getId(), notification.getChannel());
            notification.markFailed("지원하지 않는 채널: " + notification.getChannel(), null);
            notificationRepository.save(notification);
            return;
        }

        // 3. 발송 시도 + 상태 반영
        try {
            sender.send(notification);
            notification.markSuccess();
            notificationRepository.save(notification);

            log.info("알림 발송 성공. notificationId={}, channel={}",
                    notification.getId(), notification.getChannel());

        } catch (Exception e) {
            // 현재 단계: 발송 실패 → 즉시 FAILED (재시도 없음).
            // 다음 단계(retry 구현 시): markFailed(message, nextRetryAt) 로 교체.
            log.warn("알림 발송 실패. notificationId={}, error={}",
                    notification.getId(), e.getMessage());
            notification.markFailed(e.getMessage(), null);
            notificationRepository.save(notification);
        }
    }
}

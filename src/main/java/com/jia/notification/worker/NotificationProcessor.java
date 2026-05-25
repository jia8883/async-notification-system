package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;
import com.jia.notification.repository.NotificationRepository;
import com.jia.notification.worker.sender.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
     * 알림 단건 처리.
     *
     * [수정 이유]
     * poll()에서 전달받은 notification은 findPendingForUpdate()의 트랜잭션이 닫힌 후
     * detached 상태가 됨. detached 엔티티를 그대로 수정하면 save()가 merge()를 호출하지만
     * 반환된 managed 인스턴스를 무시하면 이후 변경이 반영되지 않음.
     *
     * → process() 진입 시 ID로 재조회해 현재 트랜잭션의 managed 엔티티를 확보.
     *   이후 dirty checking이 정상 동작하므로 명시적 save() 없이도 커밋 시 반영됨.
     */
    @Transactional
    public void process(Notification detachedNotification) {
        Long id = detachedNotification.getId();

        // 1. 현재 트랜잭션에서 managed 엔티티 확보
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "처리 대상 알림을 찾을 수 없습니다. id=" + id));

        // 2. 상태 재확인 — FOR UPDATE SKIP LOCKED와 poll() 사이 간격에서
        //    다른 인스턴스가 이미 처리했을 가능성 방어
        if (notification.getStatus() != NotificationStatus.PENDING) {
            log.debug("이미 처리된 알림 skip. notificationId={}, status={}",
                    id, notification.getStatus());
            return;
        }

        // 3. PROCESSING 전이 — dirty checking으로 트랜잭션 커밋 시 반영
        notification.startProcessing();

        // 4. 채널 Sender 조회
        NotificationSender sender = senderMap.get(notification.getChannel());
        if (sender == null) {
            log.error("지원하지 않는 채널. notificationId={}, channel={}",
                    id, notification.getChannel());
            notification.markFailed("지원하지 않는 채널: " + notification.getChannel(), null);
            return;
        }

        // 5. 발송 시도
        try {
            sender.send(notification);
            notification.markSuccess();
            log.info("알림 발송 성공. notificationId={}, channel={}", id, notification.getChannel());

        } catch (Exception e) {
            // RetryPolicy가 currentRetryCount 기준으로 nextRetryAt 계산.
            // null 반환 시 → markFailed() 내부에서 FAILED 전이.
            // 값 반환 시 → PENDING 전이 + nextRetryAt 저장 → 다음 polling 주기에 재처리.
            LocalDateTime nextRetryAt = RetryPolicy.nextRetryAt(notification.getRetryCount());
            notification.markFailed(e.getMessage(), nextRetryAt);

            if (nextRetryAt != null) {
                log.warn("알림 발송 실패, {}분 후 재시도 예정. notificationId={}, retryCount={}, error={}",
                        java.time.Duration.between(LocalDateTime.now(), nextRetryAt).toMinutes(),
                        id, notification.getRetryCount(), e.getMessage());
            } else {
                log.error("알림 발송 최종 실패. notificationId={}, retryCount={}, error={}",
                        id, notification.getRetryCount(), e.getMessage());
            }
        }
        // 6. 트랜잭션 커밋 시 dirty checking으로 변경사항 자동 반영
        //    명시적 save() 불필요 (managed 엔티티이므로)
    }
}

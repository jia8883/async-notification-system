package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;
import com.jia.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * stale PROCESSING 단건 복구 — 트랜잭션 경계.
 *
 * NotificationRecoveryWorker와 분리한 이유:
 * NotificationProcessor와 동일하게 self-invocation 문제 회피 +
 * 1건 복구 실패가 나머지 복구 대상에 영향을 주지 않도록 트랜잭션 격리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRecoveryProcessor {

    private final NotificationRepository notificationRepository;

    /**
     * stale PROCESSING → PENDING 복구.
     *
     * retry_count를 증가시키지 않는 이유:
     *   stale recovery는 비즈니스 실패가 아닌 인프라 장애(서버 크래시 등)로 인한 중단.
     *   retry 횟수를 소모하면 실제 발송 기회가 줄어드므로 count는 유지.
     *
     * last_error_message를 덮어쓰지 않는 이유:
     *   이전 실패 이력이 보존되어야 디버깅에 유용.
     *   서버 크래시 시엔 어차피 에러 메시지가 없으므로 덮어쓸 내용도 없음.
     */
    @Transactional
    public void recover(Notification detachedNotification) {
        Long id = detachedNotification.getId();

        // 1. 현재 트랜잭션에서 managed 엔티티 확보
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "복구 대상 알림을 찾을 수 없습니다. id=" + id));

        // 2. 상태 재확인 — findStaleProcessing 조회와 이 시점 사이에
        //    PollingWorker가 이미 처리를 완료했을 가능성 방어.
        //    (ex. 조회 직후 해당 Worker가 SUCCESS로 전이한 경우)
        if (notification.getStatus() != NotificationStatus.PROCESSING) {
            log.debug("복구 불필요 (이미 상태 변경됨). notificationId={}, status={}",
                    id, notification.getStatus());
            return;
        }

        // 3. PENDING으로 복구 — dirty checking으로 커밋 시 반영
        notification.recoverToRetry();

        log.warn("stale PROCESSING 복구 완료. notificationId={}, retryCount={}. "
                        + "다음 polling 주기에 재처리됩니다.",
                id, notification.getRetryCount());

        // 4. 트랜잭션 커밋 시 dirty checking으로 자동 반영
        //    명시적 save() 불필요
    }
}


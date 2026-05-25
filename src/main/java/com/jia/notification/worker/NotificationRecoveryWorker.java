package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * stale PROCESSING 복구 워커.
 *
 * 역할: PROCESSING 상태가 일정 시간 이상 지속된 알림을 PENDING으로 복구.
 * 대상: 서버 크래시, OOM, 강제 종료 등으로 Worker가 중단된 경우.
 *
 * PollingWorker와 의도적으로 동일한 구조를 유지:
 *   Worker → 조회 + 루프
 *   Processor → 단건 트랜잭션
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRecoveryWorker {

    private static final int BATCH_SIZE = 10;

    private final NotificationRepository notificationRepository;
    private final NotificationRecoveryProcessor recoveryProcessor;

    // application.yml에서 조정 가능. 기본값 5분.
    // 운영 환경에서 Worker 처리 시간이 길 경우 늘려야 함.
    // ex) 외부 Email API 타임아웃이 3분이라면 staleThresholdMinutes >= 4 권장.
    @Value("${notification.recovery.stale-threshold-minutes:5}")
    private long staleThresholdMinutes;

    // -------------------------------------------------------------------------
    // fixedDelay = 1분: PollingWorker(5초)보다 느린 주기로 실행.
    // Recovery는 실시간성보다 정확성이 중요하므로 빠른 주기가 불필요.
    // 1분이면 stale 기준(5분)보다 충분히 빠르게 대응 가능.
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        try {
            LocalDateTime staleThreshold = LocalDateTime.now()
                    .minusMinutes(staleThresholdMinutes);

            List<Notification> staleList = notificationRepository
                    .findStaleProcessing(staleThreshold, BATCH_SIZE);

            if (staleList.isEmpty()) {
                return;
            }

            log.warn("stale PROCESSING 감지: {}건. staleThreshold={}",
                    staleList.size(), staleThreshold);

            for (Notification stale : staleList) {
                // 건별 트랜잭션. 1건 복구 실패해도 나머지 계속 진행.
                try {
                    recoveryProcessor.recover(stale);
                } catch (Exception e) {
                    log.error("stale 복구 중 예외. notificationId={}, error={}",
                            stale.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            // DB 조회 실패. 다음 주기에 재시도.
            log.error("Recovery Worker 조회 실패. 다음 주기에 재시도됩니다.", e);
        }
    }
}

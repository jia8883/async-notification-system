package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPollingWorker {

    private static final int BATCH_SIZE = 10;

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor notificationProcessor;

    // -------------------------------------------------------------------------
    // fixedDelay = 5초: 이전 실행 완료 후 5초 대기.
    //
    // fixedRate 대신 fixedDelay를 쓰는 이유:
    //   fixedRate는 실행 시간이 interval을 초과하면 즉시 다음 실행이 시작됨.
    //   처리가 오래 걸릴 때 실행이 누적되어 DB 부하가 커질 수 있음.
    //   fixedDelay는 이전 실행이 끝난 후 기다리므로 안전.
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        try {
            List<Notification> pending = notificationRepository
                    .findPendingForUpdate(LocalDateTime.now(), BATCH_SIZE);

            if (pending.isEmpty()) {
                return;
            }

            log.debug("Polling: {}건 처리 시작", pending.size());

            for (Notification notification : pending) {
                // 건별로 별도 트랜잭션.
                // 1건 실패해도 나머지 처리 계속.
                try {
                    notificationProcessor.process(notification);
                } catch (Exception e) {
                    log.error("알림 처리 중 예외. notificationId={}, error={}",
                            notification.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            // DB 조회 자체 실패. 다음 polling 주기에 재시도.
            log.error("Polling 조회 실패. 다음 주기에 재시도됩니다.", e);
        }
    }
}

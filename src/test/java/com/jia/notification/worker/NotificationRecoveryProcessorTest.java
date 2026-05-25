package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;
import com.jia.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationRecoveryProcessorTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationRecoveryProcessor recoveryProcessor;

    @BeforeEach
    void setUp() {
        recoveryProcessor = new NotificationRecoveryProcessor(notificationRepository);
    }

    // -------------------------------------------------------------------------
    // 픽스처 헬퍼
    // -------------------------------------------------------------------------

    private Notification processingNotification() {
        Notification n = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("EMAIL")
                .title("수강 신청 완료")
                .content("수강 신청이 완료되었습니다.")
                .build();
        n.startProcessing();
        given(notificationRepository.findById(n.getId())).willReturn(Optional.of(n));
        return n;
    }

    // -------------------------------------------------------------------------
    // 정상 복구 흐름
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("stale PROCESSING 알림을 PENDING으로 복구한다")
    void recover_staleProcessing_returnsToPending() {
        Notification notification = processingNotification();

        recoveryProcessor.recover(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("recovery 시 retryCount는 증가하지 않는다 — 인프라 장애는 retry 횟수를 소모하지 않음")
    void recover_doesNotIncrementRetryCount() {
        Notification notification = processingNotification();
        int retryCountBefore = notification.getRetryCount();

        recoveryProcessor.recover(notification);

        assertThat(notification.getRetryCount()).isEqualTo(retryCountBefore);
    }

    @Test
    @DisplayName("recovery 후 nextRetryAt이 null이다 — 복구된 알림은 즉시 재처리 가능")
    void recover_setsNextRetryAtToNull() {
        Notification notification = processingNotification();

        recoveryProcessor.recover(notification);

        assertThat(notification.getNextRetryAt()).isNull();
    }

    // -------------------------------------------------------------------------
    // 상태 재확인 방어 — 조회와 처리 사이 간격에서 다른 Worker가 완료한 경우
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("이미 SUCCESS 상태면 복구를 skip하고 상태를 변경하지 않는다")
    void recover_alreadySuccess_skipped() {
        Notification notification = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("EMAIL")
                .title("title")
                .content("content")
                .build();
        notification.startProcessing();
        notification.markSuccess(); // 이미 완료된 상태
        given(notificationRepository.findById(notification.getId()))
                .willReturn(Optional.of(notification));

        recoveryProcessor.recover(notification);

        // 상태 변화 없음
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
    }

    @Test
    @DisplayName("이미 PENDING 상태면 복구를 skip하고 상태를 변경하지 않는다")
    void recover_alreadyPending_skipped() {
        // PENDING 상태인 알림 (다른 Worker가 이미 복구한 상황)
        Notification notification = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("EMAIL")
                .title("title")
                .content("content")
                .build();
        // 초기 상태가 PENDING이므로 startProcessing 없음
        given(notificationRepository.findById(notification.getId()))
                .willReturn(Optional.of(notification));

        recoveryProcessor.recover(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // retry 이력이 있는 알림 복구
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("이전 retry 이력이 있는 PROCESSING 알림도 retryCount 유지하며 복구된다")
    void recover_withExistingRetryHistory_preservesRetryCount() {
        Notification notification = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("EMAIL")
                .title("title")
                .content("content")
                .build();
        // 2번 실패 후 재시도 대기 → Worker가 다시 PROCESSING으로 전이 → 서버 크래시
        notification.markFailed("1차 실패", LocalDateTime.now().minusMinutes(5));
        notification.markFailed("2차 실패", LocalDateTime.now().minusMinutes(1));
        notification.startProcessing();
        given(notificationRepository.findById(notification.getId()))
                .willReturn(Optional.of(notification));

        recoveryProcessor.recover(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isEqualTo(2); // recovery로 증가하지 않음
    }
}


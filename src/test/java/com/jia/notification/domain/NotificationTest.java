package com.jia.notification.domain;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notification Entity 상태 전이 단위 테스트.
 *
 * Spring 컨텍스트 없음. 순수 Java 테스트.
 * 상태 전이 규칙이 Entity 안에 집중되어 있으므로 여기서 모든 분기를 검증.
 */
class NotificationTest {

    // -------------------------------------------------------------------------
    // 테스트 픽스처
    // -------------------------------------------------------------------------

    private Notification createPendingNotification() {
        return Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("EMAIL")
                .title("수강 신청 완료")
                .content("수강 신청이 완료되었습니다.")
                .build();
    }

    // -------------------------------------------------------------------------
    // startProcessing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("startProcessing() 호출 시 PROCESSING 상태로 전이된다")
    void startProcessing_changesStatusToProcessing() {
        Notification notification = createPendingNotification();

        notification.startProcessing();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    // -------------------------------------------------------------------------
    // markSuccess
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markSuccess() 호출 시 SUCCESS 상태로 전이되고 lastErrorMessage가 null이 된다")
    void markSuccess_changesStatusToSuccess() {
        Notification notification = createPendingNotification();
        notification.startProcessing();

        notification.markSuccess();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        assertThat(notification.getLastErrorMessage()).isNull();
    }

    // -------------------------------------------------------------------------
    // markFailed — retry 가능 분기
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("markFailed() — 재시도 가능한 경우")
    class MarkFailedWithRetry {

        @Test
        @DisplayName("nextRetryAt이 있으면 PENDING으로 전이되고 retryCount가 증가한다")
        void markFailed_withNextRetryAt_returnsToPending() {
            Notification notification = createPendingNotification();
            LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(1);

            notification.markFailed("연결 오류", nextRetryAt);

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getRetryCount()).isEqualTo(1);
            assertThat(notification.getNextRetryAt()).isEqualTo(nextRetryAt);
            assertThat(notification.getLastErrorMessage()).isEqualTo("연결 오류");
        }

        @Test
        @DisplayName("3번 실패해도 nextRetryAt이 있으면 PENDING 상태를 유지한다")
        void markFailed_repeatedWithRetry_staysPending() {
            Notification notification = createPendingNotification();

            notification.markFailed("err", LocalDateTime.now().plusMinutes(1));
            notification.markFailed("err", LocalDateTime.now().plusMinutes(5));
            notification.markFailed("err", LocalDateTime.now().plusMinutes(15));

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getRetryCount()).isEqualTo(3);
        }
    }

    // -------------------------------------------------------------------------
    // markFailed — 최종 실패 분기
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("markFailed() — 최종 실패인 경우")
    class MarkFailedFinal {

        @Test
        @DisplayName("nextRetryAt이 null이면 FAILED로 전이되고 retryCount가 증가한다")
        void markFailed_withoutNextRetryAt_becomeFailed() {
            Notification notification = createPendingNotification();

            notification.markFailed("최대 재시도 초과", null);

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.getRetryCount()).isEqualTo(1);
            assertThat(notification.getNextRetryAt()).isNull();
            assertThat(notification.getLastErrorMessage()).isEqualTo("최대 재시도 초과");
        }
    }

    // -------------------------------------------------------------------------
    // recoverToRetry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("recoverToRetry() 호출 시 PENDING으로 복구되고 retryCount는 증가하지 않는다")
    void recoverToRetry_returnsToPendingWithoutRetryCountIncrease() {
        Notification notification = createPendingNotification();
        notification.startProcessing();
        int retryCountBefore = notification.getRetryCount();

        notification.recoverToRetry();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isEqualTo(retryCountBefore); // 증가 없음
        assertThat(notification.getNextRetryAt()).isNull(); // 즉시 재처리 가능
    }

    // -------------------------------------------------------------------------
    // markAsRead
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markAsRead() 호출 시 읽음 처리되고 readAt이 설정된다")
    void markAsRead_setsIsReadAndReadAt() {
        Notification notification = createPendingNotification();

        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsRead()를 여러 번 호출해도 readAt은 최초 시각을 유지한다 (idempotent)")
    void markAsRead_calledMultipleTimes_readAtNotOverwritten() {
        Notification notification = createPendingNotification();

        notification.markAsRead();
        LocalDateTime firstReadAt = notification.getReadAt();

        notification.markAsRead(); // 두 번째 호출

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }
}


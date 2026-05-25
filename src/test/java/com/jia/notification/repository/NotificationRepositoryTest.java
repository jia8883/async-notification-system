package com.jia.notification.repository;


import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationRepository 통합 테스트.
 *
 * @DataJpaTest를 쓰는 이유:
 *   JPA 관련 Bean만 로드. @SpringBootTest보다 훨씬 빠름.
 *
 * replace = NONE을 쓰는 이유:
 *   FOR UPDATE SKIP LOCKED, Partial Index 등 PostgreSQL 전용 기능은
 *   H2 인메모리 DB에서 동작하지 않음. 실제 PostgreSQL 필요.
 *   docker-compose up 후 테스트 실행 필요.
 *
 * 실행 방법:
 *   docker-compose up -d postgres-test
 *   ./gradlew test --tests "*.NotificationRepositoryTest"
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    // -------------------------------------------------------------------------
    // 픽스처 헬퍼
    // -------------------------------------------------------------------------

    private Notification save(String channel, NotificationStatus status) {
        return save(channel, status, null);
    }

    private Notification save(String channel, NotificationStatus status, LocalDateTime nextRetryAt) {
        Notification n = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(System.nanoTime()) // unique constraint 회피용 unique referenceId
                .channel(channel)
                .title("title")
                .content("content")
                .build();

        // 상태 전이가 필요한 경우
        if (status == NotificationStatus.PROCESSING) {
            n.startProcessing();
        } else if (status == NotificationStatus.SUCCESS) {
            n.startProcessing();
            n.markSuccess();
        } else if (status == NotificationStatus.FAILED) {
            n.markFailed("test failure", null);
        }

        if (nextRetryAt != null) {
            // PENDING + nextRetryAt 설정 (retry 대기 중)
            n.markFailed("retry test", nextRetryAt);
        }

        return notificationRepository.saveAndFlush(n);
    }

    // -------------------------------------------------------------------------
    // findPendingForUpdate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PENDING 알림을 조회한다")
    void findPendingForUpdate_returnsPendingNotifications() {
        save("EMAIL", NotificationStatus.PENDING);
        save("EMAIL", NotificationStatus.SUCCESS); // 조회 대상 아님

        List<Notification> result = notificationRepository
                .findPendingForUpdate(LocalDateTime.now(), 10);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(n -> n.getStatus() == NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("next_retry_at이 미래인 PENDING은 조회되지 않는다")
    void findPendingForUpdate_excludesFutureRetry() {
        // next_retry_at = 1시간 후 → 아직 처리 불가
        save("EMAIL", NotificationStatus.PENDING, LocalDateTime.now().plusHours(1));

        List<Notification> result = notificationRepository
                .findPendingForUpdate(LocalDateTime.now(), 10);

        // 미래 nextRetryAt을 가진 건은 포함되지 않아야 함
        assertThat(result).noneMatch(n ->
                n.getNextRetryAt() != null && n.getNextRetryAt().isAfter(LocalDateTime.now())
        );
    }

    @Test
    @DisplayName("next_retry_at이 현재 이전인 PENDING은 조회된다 — retry 대기 완료")
    void findPendingForUpdate_includesPastRetry() {
        // next_retry_at = 1분 전 → 재처리 가능
        Notification retryReady = save("EMAIL", NotificationStatus.PENDING,
                LocalDateTime.now().minusMinutes(1));

        List<Notification> result = notificationRepository
                .findPendingForUpdate(LocalDateTime.now(), 10);

        assertThat(result).extracting(Notification::getId)
                .contains(retryReady.getId());
    }

    @Test
    @DisplayName("LIMIT 이상의 PENDING이 있어도 limit 건수만 조회된다")
    void findPendingForUpdate_respectsLimit() {
        for (int i = 0; i < 5; i++) {
            save("EMAIL", NotificationStatus.PENDING);
        }

        List<Notification> result = notificationRepository
                .findPendingForUpdate(LocalDateTime.now(), 3);

        assertThat(result).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // findStaleProcessing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("staleThreshold보다 오래된 PROCESSING 알림을 조회한다")
    void findStaleProcessing_returnsStaleNotifications() {
        Notification stale = save("EMAIL", NotificationStatus.PROCESSING);

        // staleThreshold = 지금으로부터 1초 후 → 방금 저장한 건이 stale로 판단됨
        LocalDateTime staleThreshold = LocalDateTime.now().plusSeconds(1);
        List<Notification> result = notificationRepository
                .findStaleProcessing(staleThreshold, 10);

        assertThat(result).extracting(Notification::getId)
                .contains(stale.getId());
    }

    @Test
    @DisplayName("최근에 PROCESSING 전이된 알림은 stale 조회에서 제외된다")
    void findStaleProcessing_excludesRecentProcessing() {
        save("EMAIL", NotificationStatus.PROCESSING);

        // staleThreshold = 10분 전 → 방금 저장한 건은 stale 아님
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(10);
        List<Notification> result = notificationRepository
                .findStaleProcessing(staleThreshold, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("SUCCESS 상태는 stale 조회 대상이 아니다")
    void findStaleProcessing_excludesNonProcessingStatus() {
        save("EMAIL", NotificationStatus.SUCCESS);

        LocalDateTime staleThreshold = LocalDateTime.now().plusSeconds(1);
        List<Notification> result = notificationRepository
                .findStaleProcessing(staleThreshold, 10);

        assertThat(result).noneMatch(n -> n.getStatus() == NotificationStatus.SUCCESS);
    }

    // -------------------------------------------------------------------------
    // 수신자별 목록 조회
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("수신자 ID로 알림 목록을 최신순으로 조회한다")
    void findByRecipientId_returnsOrderedByCreatedAtDesc() {
        Long recipientId = 999L;

        Notification n1 = notificationRepository.saveAndFlush(
                Notification.builder()
                        .recipientId(recipientId)
                        .eventType("EVENT_A")
                        .referenceId(1L)
                        .channel("EMAIL")
                        .title("첫 번째")
                        .content("content")
                        .build()
        );
        Notification n2 = notificationRepository.saveAndFlush(
                Notification.builder()
                        .recipientId(recipientId)
                        .eventType("EVENT_B")
                        .referenceId(2L)
                        .channel("EMAIL")
                        .title("두 번째")
                        .content("content")
                        .build()
        );

        List<Notification> result = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId);

        assertThat(result).hasSize(2);
        // 최신순: n2가 n1보다 나중에 저장됐으므로 앞에 와야 함
        assertThat(result.get(0).getId()).isEqualTo(n2.getId());
    }
}


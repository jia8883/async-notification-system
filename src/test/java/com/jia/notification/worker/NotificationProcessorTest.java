package com.jia.notification.worker;

import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;
import com.jia.notification.repository.NotificationRepository;
import com.jia.notification.worker.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * NotificationProcessor 단위 테스트.
 *
 * @SpringBootTest 대신 @ExtendWith(MockitoExtension)을 쓰는 이유:
 *   Processor는 Repository와 Sender에만 의존하므로 전체 컨텍스트 불필요.
 *   Mock으로 의존성을 대체하면 테스트가 빠르고 시나리오 제어가 쉬움.
 *
 * @Transactional 동작은 테스트하지 않음:
 *   트랜잭션 경계 자체는 통합 테스트 영역. 여기서는 "상태 전이 로직"만 검증.
 */
@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender emailSender;

    private NotificationProcessor processor;

    @BeforeEach
    void setUp() {
        given(emailSender.supportedChannel()).willReturn("EMAIL");
        processor = new NotificationProcessor(notificationRepository, List.of(emailSender));
    }

    // -------------------------------------------------------------------------
    // 공통 픽스처 생성 헬퍼
    // -------------------------------------------------------------------------

    private Notification pendingNotification() {
        Notification n = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("EMAIL")
                .title("수강 신청 완료")
                .content("수강 신청이 완료되었습니다.")
                .build();
        given(notificationRepository.findById(n.getId())).willReturn(Optional.of(n));
        return n;
    }

    // -------------------------------------------------------------------------
    // 정상 발송 흐름
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("정상 발송 시 SUCCESS 상태로 전이된다")
    void process_success() {
        Notification notification = pendingNotification();
        willDoNothing().given(emailSender).send(any());

        processor.process(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUCCESS);
        assertThat(notification.getRetryCount()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // 발송 실패 + retry 흐름
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("발송 실패 시나리오")
    class SendFailure {

        @Test
        @DisplayName("1차 실패 시 retryCount=1, PENDING, nextRetryAt=1분 후로 전이된다")
        void process_firstFailure_pendingWithRetry() {
            Notification notification = pendingNotification();
            willThrow(new RuntimeException("연결 오류")).given(emailSender).send(any());

            processor.process(notification);

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getRetryCount()).isEqualTo(1);
            assertThat(notification.getNextRetryAt()).isNotNull();
            assertThat(notification.getNextRetryAt())
                    .isAfter(java.time.LocalDateTime.now().plusSeconds(50)); // 약 1분 후
            assertThat(notification.getLastErrorMessage()).isEqualTo("연결 오류");
        }

        @Test
        @DisplayName("최대 재시도 초과 시 FAILED 상태로 전이된다")
        void process_exceedMaxRetry_failed() {
            // retryCount를 MAX에 도달시킨 Notification 준비
            // markFailed를 직접 호출해 retryCount를 3으로 만듦
            Notification notification = pendingNotification();
            notification.markFailed("1차", java.time.LocalDateTime.now().plusMinutes(1));
            notification.markFailed("2차", java.time.LocalDateTime.now().plusMinutes(5));
            notification.markFailed("3차", java.time.LocalDateTime.now().plusMinutes(15));
            // 이제 retryCount = 3 (MAX_RETRY_COUNT)

            willThrow(new RuntimeException("또 실패")).given(emailSender).send(any());

            processor.process(notification);

            // RetryPolicy.nextRetryAt(3) = null → FAILED
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notification.getRetryCount()).isEqualTo(4); // markFailed 내부에서 ++
            assertThat(notification.getNextRetryAt()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // 지원하지 않는 채널
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("지원하지 않는 채널이면 sender를 호출하지 않고 FAILED로 전이된다")
    void process_unsupportedChannel_failed() {
        Notification notification = Notification.builder()
                .recipientId(1L)
                .eventType("COURSE_REGISTERED")
                .referenceId(100L)
                .channel("SMS") // 미지원 채널
                .title("title")
                .content("content")
                .build();
        given(notificationRepository.findById(notification.getId()))
                .willReturn(Optional.of(notification));

        processor.process(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastErrorMessage()).contains("SMS");
        verify(emailSender, never()).send(any());
    }

    // -------------------------------------------------------------------------
    // 이미 처리된 알림 방어
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PROCESSING 상태인 알림은 처리를 skip하고 sender를 호출하지 않는다")
    void process_alreadyProcessing_skipped() {
        Notification notification = pendingNotification();
        notification.startProcessing(); // 이미 누군가 처리 중

        processor.process(notification);

        // 상태 변화 없고 sender도 호출 안 됨
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
        verify(emailSender, never()).send(any());
    }
}


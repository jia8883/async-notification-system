package com.jia.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notification",
        uniqueConstraints = {
                // 동일 이벤트에 대한 중복 발송 방지 (Idempotency)
                // 같은 수신자 + 이벤트 타입 + 참조 ID + 채널 조합은 DB 레벨에서 1개만 허용
                @UniqueConstraint(
                        name = "uq_notification_idempotency",
                        columnNames = {"recipient_id", "event_type", "reference_id", "channel"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수신자 ID (User 테이블 참조 없이 ID만 보관 — 과제 범위에서 JOIN 불필요)
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    // 이벤트 타입 (예: COURSE_REGISTERED, PAYMENT_CONFIRMED)
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    // 이벤트가 참조하는 도메인 ID (강의 ID, 결제 ID 등)
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    // 발송 채널 (EMAIL / IN_APP)
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    // 재시도 횟수. 최초 0, 시도마다 +1
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    // 다음 재시도 가능 시각. null이면 즉시 처리 가능
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // 마지막 실패 사유. 성공 시에도 유지 (히스토리 용도)
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    // 읽음 여부 (SUCCESS 상태에서만 의미 있음)
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // 생성
    // -------------------------------------------------------------------------

    @Builder
    private Notification(
            Long recipientId,
            String eventType,
            Long referenceId,
            String channel,
            String title,
            String content
    ) {
        this.recipientId = recipientId;
        this.eventType = eventType;
        this.referenceId = referenceId;
        this.channel = channel;
        this.title = title;
        this.content = content;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.isRead = false;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------------
    // 상태 전이 메서드
    // Worker/Scheduler가 직접 필드를 건드리지 않고 Entity 메서드를 통해 상태를 변경.
    // 이렇게 하면 상태 전이 규칙이 Entity 안에 집중되고 테스트가 쉬워진다.
    // -------------------------------------------------------------------------

    /**
     * Worker가 처리를 시작할 때 호출.
     */
    public void startProcessing() {
        this.status = NotificationStatus.PROCESSING;
    }

    /**
     * 발송 성공 시 호출.
     */
    public void markSuccess() {
        this.status = NotificationStatus.SUCCESS;
        this.lastErrorMessage = null;
    }

    /**
     * 발송 실패 시 호출.
     * 재시도 가능 여부에 따라 PENDING(대기) 또는 FAILED(최종 실패)로 전이.
     *
     * @param errorMessage 실패 사유
     * @param nextRetryAt  재시도 가능 시각 (null이면 최종 실패)
     */
    public void markFailed(String errorMessage, LocalDateTime nextRetryAt) {
        this.retryCount++;
        this.lastErrorMessage = errorMessage;

        if (nextRetryAt != null) {
            this.status = NotificationStatus.PENDING;
            this.nextRetryAt = nextRetryAt;
        } else {
            this.status = NotificationStatus.FAILED;
            this.nextRetryAt = null;
        }
    }

    /**
     * stale PROCESSING 복구 시 호출.
     * PROCESSING 상태로 오래 머문 레코드를 PENDING으로 되돌린다.
     */
    public void recoverToRetry() {
        this.status = NotificationStatus.PENDING;
        this.nextRetryAt = null;
    }

    /**
     * 읽음 처리.
     * 여러 기기에서 동시에 호출되어도 is_read = true 는 idempotent.
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }
}


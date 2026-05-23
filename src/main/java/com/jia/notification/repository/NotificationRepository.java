package com.jia.notification.repository;

import com.jia.notification.domain.Notification;
import com.jia.notification.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // -------------------------------------------------------------------------
    // API 조회용
    // -------------------------------------------------------------------------

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(Long recipientId, boolean isRead);

    // -------------------------------------------------------------------------
    // Worker polling용
    //
    // SKIP LOCKED: 다른 Worker 인스턴스가 이미 락을 잡은 row는 건너뜀.
    // → 다중 인스턴스에서 동일 알림 중복 처리 방지.
    //
    // next_retry_at IS NULL OR next_retry_at <= now: 즉시 처리 가능한 것만 선택.
    // → 재시도 대기 중인 알림은 지정 시각 이전에 처리되지 않음.
    //
    // LIMIT은 @Query에서 직접 사용이 제한적이므로, 호출부에서 Pageable로 처리하거나
    // nativeQuery=true로 처리. 여기서는 nativeQuery 사용 (SKIP LOCKED가 JPQL 미지원).
    // -------------------------------------------------------------------------

    @Query(
            value = """
            SELECT * FROM notification
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<Notification> findPendingForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    // -------------------------------------------------------------------------
    // Recovery용
    //
    // PROCESSING 상태가 일정 시간 이상 지속되면 서버 재시작 등으로 중단된 것으로 판단.
    // 해당 레코드를 PENDING으로 복구. 마찬가지로 SKIP LOCKED 적용.
    // -------------------------------------------------------------------------

    @Query(
            value = """
            SELECT * FROM notification
            WHERE status = 'PROCESSING'
              AND updated_at < :staleThreshold
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<Notification> findStaleProcessing(
            @Param("staleThreshold") LocalDateTime staleThreshold,
            @Param("limit") int limit
    );

    // -------------------------------------------------------------------------
    // 통계 / 모니터링용 (Actuator 등에서 활용 가능)
    // -------------------------------------------------------------------------

    long countByStatus(NotificationStatus status);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = 'FAILED'")
    long countFailed();
}

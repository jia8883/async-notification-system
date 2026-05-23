-- V1__create_notification_table.sql

CREATE TABLE notification
(
    id                  BIGSERIAL       NOT NULL,
    recipient_id        BIGINT          NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    reference_id        BIGINT          NOT NULL,
    channel             VARCHAR(20)     NOT NULL,
    title               VARCHAR(200)    NOT NULL,
    content             TEXT            NOT NULL,

    -- 상태 관리
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    retry_count         INT             NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMP,
    last_error_message  TEXT,

    -- 읽음 처리
    is_read             BOOLEAN         NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMP,

    -- 감사 컬럼
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification PRIMARY KEY (id),

    -- Idempotency: 동일 이벤트에 대한 중복 알림 생성 방지
    -- (수신자 + 이벤트 타입 + 참조 ID + 채널) 조합으로 유일성 보장
    CONSTRAINT uq_notification_idempotency
        UNIQUE (recipient_id, event_type, reference_id, channel)
);

-- Worker polling 인덱스
-- status + next_retry_at 조합으로 PENDING 조회 성능 확보
-- FOR UPDATE SKIP LOCKED와 함께 사용
CREATE INDEX idx_notification_polling
    ON notification (status, next_retry_at)
    WHERE status = 'PENDING';

-- Recovery 인덱스
-- PROCESSING 상태 + updated_at 으로 stale 레코드 조회
CREATE INDEX idx_notification_recovery
    ON notification (status, updated_at)
    WHERE status = 'PROCESSING';

-- 사용자별 알림 목록 조회 인덱스
CREATE INDEX idx_notification_recipient
    ON notification (recipient_id, created_at DESC);

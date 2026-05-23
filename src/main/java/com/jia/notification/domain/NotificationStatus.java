package com.jia.notification.domain;

public enum NotificationStatus {

    /**
     * 발송 대기 중. Worker가 다음 polling 때 처리 대상.
     * 최초 생성 시 및 재시도 대기 시 사용.
     */
    PENDING,

    /**
     * Worker가 현재 처리 중.
     * 일정 시간 이상 이 상태로 머물면 stale로 판단 → PENDING 복구 대상.
     */
    PROCESSING,

    /**
     * 발송 성공. is_read / read_at 으로 읽음 여부를 별도 관리.
     */
    SUCCESS,

    /**
     * 최대 재시도 횟수 초과 후 최종 실패.
     * last_error_message 에 마지막 실패 사유 기록.
     */
    FAILED
}


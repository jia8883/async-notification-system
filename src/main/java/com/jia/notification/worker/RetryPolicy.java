package com.jia.notification.worker;

import java.time.LocalDateTime;

/**
 * 알림 재시도 정책.
 *
 * 설계 원칙:
 * - 순수 계산 유틸. 상태 없음, Spring Bean 아님.
 * - "몇 번 재시도할 수 있는가"와 "다음 시도 시각은 언제인가"만 책임짐.
 * - Entity/Processor가 이 클래스를 호출해 nextRetryAt을 얻어 markFailed()에 전달.
 *
 * 정책:
 *   1차 실패 (retryCount=0 → 1) : 1분 후
 *   2차 실패 (retryCount=1 → 2) : 5분 후
 *   3차 실패 (retryCount=2 → 3) : 15분 후
 *   초과      (retryCount >= 3)  : null 반환 → FAILED
 */
public final class RetryPolicy {

    public static final int MAX_RETRY_COUNT = 3;

    // 분 단위 지연. 인덱스 = 현재 retryCount (실패 직전 시점).
    private static final long[] DELAY_MINUTES = {1, 5, 15};

    private RetryPolicy() {}

    /**
     * 다음 재시도 시각을 반환.
     *
     * @param currentRetryCount markFailed() 호출 직전의 retryCount (Entity 내부에서 ++ 전)
     * @return 재시도 가능 시각. null이면 최대 재시도 초과 → FAILED 전이.
     *
     * 호출 예시:
     *   notification.markFailed(errorMsg, RetryPolicy.nextRetryAt(notification.getRetryCount()));
     *
     * 흐름:
     *   retryCount=0 → DELAY_MINUTES[0]=1분 후 반환 → markFailed에서 retryCount=1, PENDING
     *   retryCount=1 → DELAY_MINUTES[1]=5분 후 반환 → markFailed에서 retryCount=2, PENDING
     *   retryCount=2 → DELAY_MINUTES[2]=15분 후 반환 → markFailed에서 retryCount=3, PENDING
     *   retryCount=3 → null 반환 → markFailed에서 retryCount=4, FAILED
     */
    public static LocalDateTime nextRetryAt(int currentRetryCount) {
        if (currentRetryCount >= MAX_RETRY_COUNT) {
            return null;
        }
        return LocalDateTime.now().plusMinutes(DELAY_MINUTES[currentRetryCount]);
    }

    /**
     * 현재 retryCount 기준으로 재시도 가능 여부 확인.
     * 주로 테스트나 로그에서 사용.
     */
    public static boolean canRetry(int currentRetryCount) {
        return currentRetryCount < MAX_RETRY_COUNT;
    }
}


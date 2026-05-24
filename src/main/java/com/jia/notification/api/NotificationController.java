package com.jia.notification.api;

import com.jia.notification.api.dto.CreateNotificationRequest;
import com.jia.notification.api.dto.CreateNotificationResponse;
import com.jia.notification.api.dto.NotificationResponse;
import com.jia.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // -------------------------------------------------------------------------
    // 알림 발송 요청 등록
    // POST /notifications
    //
    // 201 Created: "요청이 새로 접수됨"을 명확히 표현.
    // 409 Conflict: 동일 이벤트 중복 요청 (GlobalExceptionHandler에서 처리).
    // -------------------------------------------------------------------------

    @PostMapping("/notifications")
    public ResponseEntity<CreateNotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest request
    ) {
        CreateNotificationResponse response = notificationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // 알림 단건 상태 조회
    // GET /notifications/{id}
    // -------------------------------------------------------------------------

    @GetMapping("/notifications/{id}")
    public ResponseEntity<NotificationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getById(id));
    }

    // -------------------------------------------------------------------------
    // 사용자 알림 목록 조회
    // GET /users/{userId}/notifications?isRead=true/false
    //
    // userId를 PathVariable로 받는 이유:
    //   실제 인증이 없는 과제 범위에서 userId를 URL로 전달하는 방식이
    //   과제 명세에서 허용된 방식과 일치하며, 직관적임.
    //
    // isRead를 Boolean(nullable)으로 받는 이유:
    //   파라미터가 없으면 null → 전체 조회.
    //   true/false → 필터링. required=false가 기본값이므로 생략 가능.
    // -------------------------------------------------------------------------

    @GetMapping("/users/{userId}/notifications")
    public ResponseEntity<List<NotificationResponse>> getByUser(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean isRead
    ) {
        return ResponseEntity.ok(notificationService.getByRecipient(userId, isRead));
    }
}


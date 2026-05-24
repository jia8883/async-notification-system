package com.jia.notification.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // 도메인 예외
    // -------------------------------------------------------------------------

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    // 중복 알림 요청.
    // 409 Conflict: "요청 자체는 유효하지만 이미 존재함"을 의미.
    // 클라이언트는 이 응답을 받으면 재시도하지 않아도 됨 (이미 등록된 상태).
    @ExceptionHandler(DuplicateNotificationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateNotificationException e) {
        log.info("중복 알림 요청 무시: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(HttpStatus.CONFLICT, e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Validation 예외
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "유효하지 않은 값입니다." : fe.getDefaultMessage(),
                        (first, second) -> first  // 동일 필드 중복 에러 시 첫 번째만 유지
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(fieldErrors));
    }

    // -------------------------------------------------------------------------
    // 그 외 예외 — 의도치 않은 서버 오류
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."));
    }

    // -------------------------------------------------------------------------
    // Error Response 내부 record
    // -------------------------------------------------------------------------

    public record ErrorResponse(
            int status,
            String message,
            Map<String, String> fieldErrors,
            LocalDateTime timestamp
    ) {
        static ErrorResponse of(HttpStatus httpStatus, String message) {
            return new ErrorResponse(httpStatus.value(), message, null, LocalDateTime.now());
        }

        static ErrorResponse ofValidation(Map<String, String> fieldErrors) {
            return new ErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    "요청 값이 유효하지 않습니다.",
                    fieldErrors,
                    LocalDateTime.now()
            );
        }
    }
}


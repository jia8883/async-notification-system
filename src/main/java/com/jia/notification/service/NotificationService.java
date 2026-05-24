package com.jia.notification.service;

import com.jia.notification.api.dto.CreateNotificationRequest;
import com.jia.notification.api.dto.CreateNotificationResponse;
import com.jia.notification.api.dto.NotificationResponse;
import com.jia.notification.api.exception.DuplicateNotificationException;
import com.jia.notification.api.exception.NotificationNotFoundException;
import com.jia.notification.domain.Notification;
import com.jia.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // -------------------------------------------------------------------------
    // 알림 발송 요청 등록
    //
    // 실제 발송은 하지 않음. PENDING 상태로 저장만.
    // Worker가 주기적으로 PENDING을 조회해서 처리.
    //
    // DataIntegrityViolationException → DuplicateNotificationException 변환:
    //   DB unique constraint 위반 시 Spring이 던지는 예외를 도메인 예외로 변환.
    //   Service 계층이 DB 예외를 직접 외부로 노출하지 않도록 캡슐화.
    // -------------------------------------------------------------------------

    @Transactional
    public CreateNotificationResponse create(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .eventType(request.getEventType())
                .referenceId(request.getReferenceId())
                .channel(request.getChannel())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        try {
            Notification saved = notificationRepository.save(notification);
            log.info("알림 요청 등록 완료. id={}, recipientId={}, eventType={}, channel={}",
                    saved.getId(), saved.getRecipientId(), saved.getEventType(), saved.getChannel());
            return CreateNotificationResponse.from(saved);

        } catch (DataIntegrityViolationException e) {
            // unique constraint 위반 → 동일 이벤트에 대한 중복 요청
            // 409 Conflict 으로 반환. 비즈니스 트랜잭션을 실패시키지 않음.
            log.warn("중복 알림 요청 감지. eventType={}, referenceId={}, recipientId={}, channel={}",
                    request.getEventType(), request.getReferenceId(),
                    request.getRecipientId(), request.getChannel());

            throw new DuplicateNotificationException(
                    request.getEventType(),
                    request.getReferenceId(),
                    request.getRecipientId(),
                    request.getChannel()
            );
        }
    }

    // -------------------------------------------------------------------------
    // 단건 상태 조회
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        return NotificationResponse.from(notification);
    }

    // -------------------------------------------------------------------------
    // 사용자 알림 목록 조회
    //
    // isRead null → 전체 조회
    // isRead true/false → 필터링
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByRecipient(Long recipientId, Boolean isRead) {
        List<Notification> notifications = (isRead == null)
                ? notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                : notificationRepository.findByRecipientIdAndIsReadOrderByCreatedAtDesc(recipientId, isRead);

        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }
}


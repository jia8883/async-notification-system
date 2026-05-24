package com.jia.notification.api.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateNotificationRequest {

    @NotNull(message = "수신자 ID는 필수입니다.")
    @Positive(message = "수신자 ID는 양수여야 합니다.")
    private Long recipientId;

    @NotBlank(message = "이벤트 타입은 필수입니다.")
    @Size(max = 50, message = "이벤트 타입은 50자 이하여야 합니다.")
    private String eventType;

    @NotNull(message = "참조 ID는 필수입니다.")
    @Positive(message = "참조 ID는 양수여야 합니다.")
    private Long referenceId;

    // EMAIL / IN_APP 만 허용
    @NotBlank(message = "발송 채널은 필수입니다.")
    @Pattern(regexp = "^(EMAIL|IN_APP)$", message = "발송 채널은 EMAIL 또는 IN_APP 이어야 합니다.")
    private String channel;

    @NotBlank(message = "알림 제목은 필수입니다.")
    @Size(max = 200, message = "알림 제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "알림 내용은 필수입니다.")
    private String content;
}


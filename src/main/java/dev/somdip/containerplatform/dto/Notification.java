package dev.somdip.containerplatform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Notification {
    private String type; // success, warning, error, info
    private String title;
    private String message;
    private String timeAgo;
}

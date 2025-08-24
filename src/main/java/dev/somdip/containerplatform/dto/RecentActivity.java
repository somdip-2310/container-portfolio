package dev.somdip.containerplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivity {
    private String type;
    private String containerName;
    private String action;
    private Instant timestamp;
    private String status;
    
    public String getTimeAgo() {
        long seconds = Instant.now().getEpochSecond() - timestamp.getEpochSecond();
        
        if (seconds < 60) {
            return seconds + " seconds ago";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutes ago";
        } else if (seconds < 86400) {
            return (seconds / 3600) + " hours ago";
        } else {
            return (seconds / 86400) + " days ago";
        }
    }
    
    public String getStatusColor() {
        switch (status.toUpperCase()) {
            case "RUNNING":
            case "SUCCESS":
            case "COMPLETED":
                return "green";
            case "PENDING":
            case "UPDATING":
            case "IN_PROGRESS":
                return "blue";
            case "STOPPED":
            case "PAUSED":
                return "yellow";
            case "FAILED":
            case "ERROR":
                return "red";
            default:
                return "gray";
        }
    }
}
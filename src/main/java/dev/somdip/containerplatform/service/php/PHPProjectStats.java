package dev.somdip.containerplatform.service.php;

import lombok.Builder;
import lombok.Data;

/**
 * Statistics about a PHP project
 */
@Data
@Builder
public class PHPProjectStats {
    @Builder.Default
    private int phpFileCount = 0;
    @Builder.Default
    private boolean hasComposer = false;
    @Builder.Default
    private boolean hasEnvFile = false;
}

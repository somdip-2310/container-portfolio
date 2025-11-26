package dev.somdip.containerplatform.service.php;

import lombok.Builder;
import lombok.Data;

/**
 * Information about detected PHP application structure
 */
@Data
@Builder
public class PHPApplicationInfo {
    private PHPFramework framework;
    private String phpVersion;
    private String documentRoot;
    private String entryPoint;
    private boolean requiresComposer;
}

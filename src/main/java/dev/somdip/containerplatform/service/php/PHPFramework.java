package dev.somdip.containerplatform.service.php;

/**
 * Supported PHP frameworks and application types
 */
public enum PHPFramework {
    LARAVEL("Laravel"),
    SYMFONY("Symfony"),
    CODEIGNITER("CodeIgniter"),
    WORDPRESS("WordPress"),
    GENERIC_WITH_PUBLIC("Generic PHP with public directory"),
    GENERIC("Generic PHP");

    private final String displayName;

    PHPFramework(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package dev.somdip.containerplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvVarSuggestion {
    private String name;
    private String defaultValue;
    private String description;
    private String source; // Where it was detected: DOCKERFILE, ENV_EXAMPLE, CONFIG_FILE, SOURCE_CODE
    private String framework; // Which framework/language: SPRING, NODE, PYTHON, GO, PHP, RUBY, DOTNET, GENERIC
    private boolean required;
    private boolean isSecret; // If it looks like a secret (contains KEY, SECRET, PASSWORD, TOKEN, etc.)
}

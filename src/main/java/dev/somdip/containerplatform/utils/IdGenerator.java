package dev.somdip.containerplatform.utils;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class IdGenerator {
    public String generateId() {
        return UUID.randomUUID().toString();
    }
    
    public String generateApiKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
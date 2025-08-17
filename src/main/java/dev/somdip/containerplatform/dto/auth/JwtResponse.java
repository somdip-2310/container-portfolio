package dev.somdip.containerplatform.dto.auth;
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String userId;
    private String email;
    private String apiKey;
    
    public JwtResponse(String token, String userId, String email, String apiKey) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.apiKey = apiKey;
    }
    
    // Getters and setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
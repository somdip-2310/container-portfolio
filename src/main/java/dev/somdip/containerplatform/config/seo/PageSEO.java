package dev.somdip.containerplatform.config.seo;

import lombok.Builder;
import lombok.Data;

/**
 * Model for page-specific SEO metadata
 */
@Data
@Builder
public class PageSEO {
    private String title;
    private String description;
    private String keywords;
    private String ogType;
    private String twitterCard;
    private String canonicalUrl;
    @Builder.Default
    private boolean noIndex = false;
    private String image;
    
    public String getMetaTags() {
        StringBuilder meta = new StringBuilder();
        
        // Basic meta tags
        if (title != null) {
            meta.append("<title>").append(title).append("</title>\n");
            meta.append("<meta property=\"og:title\" content=\"").append(title).append("\">\n");
            meta.append("<meta name=\"twitter:title\" content=\"").append(title).append("\">\n");
        }
        
        if (description != null) {
            meta.append("<meta name=\"description\" content=\"").append(description).append("\">\n");
            meta.append("<meta property=\"og:description\" content=\"").append(description).append("\">\n");
            meta.append("<meta name=\"twitter:description\" content=\"").append(description).append("\">\n");
        }
        
        if (keywords != null) {
            meta.append("<meta name=\"keywords\" content=\"").append(keywords).append("\">\n");
        }
        
        // OpenGraph
        if (ogType != null) {
            meta.append("<meta property=\"og:type\" content=\"").append(ogType).append("\">\n");
        }
        
        // Twitter Card
        if (twitterCard != null) {
            meta.append("<meta name=\"twitter:card\" content=\"").append(twitterCard).append("\">\n");
        } else {
            meta.append("<meta name=\"twitter:card\" content=\"summary\">\n");
        }
        
        // Canonical URL
        if (canonicalUrl != null) {
            meta.append("<link rel=\"canonical\" href=\"").append(canonicalUrl).append("\">\n");
        }
        
        // Robots
        if (noIndex) {
            meta.append("<meta name=\"robots\" content=\"noindex,nofollow\">\n");
        } else {
            meta.append("<meta name=\"robots\" content=\"index,follow\">\n");
        }
        
        // Image
        if (image != null) {
            meta.append("<meta property=\"og:image\" content=\"").append(image).append("\">\n");
            meta.append("<meta name=\"twitter:image\" content=\"").append(image).append("\">\n");
        }
        
        return meta.toString();
    }
}

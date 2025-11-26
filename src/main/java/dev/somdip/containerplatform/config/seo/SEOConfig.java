package dev.somdip.containerplatform.config.seo;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SEO Configuration for SnapDeploy
 * Contains meta tags, keywords, and descriptions for different pages
 */
@Component
public class SEOConfig {
    
    private final Map<String, PageSEO> pageConfigs = new HashMap<>();
    
    public SEOConfig() {
        initializePageConfigs();
    }
    
    private void initializePageConfigs() {
        // Homepage
        pageConfigs.put("index", PageSEO.builder()
                .title("SnapDeploy - Deploy Docker Containers in 60 Seconds | Best Heroku Alternative 2025")
                .description("Deploy your web apps instantly with SnapDeploy. Simple container hosting, auto-scaling, and SSL. 50% cheaper than Heroku. Perfect for startups and indie developers. Try free!")
                .keywords("heroku alternative, docker hosting, container hosting, cheap heroku alternative, deploy docker container, simple paas, railway alternative, render alternative")
                .ogType("website")
                .twitterCard("summary_large_image")
                .canonicalUrl("https://snapdeploy.dev/")
                .build());
        
        // Heroku Alternative Page
        pageConfigs.put("heroku-alternative", PageSEO.builder()
                .title("Best Heroku Alternative 2025 - SnapDeploy | 50% Cheaper, Same Simplicity")
                .description("Looking for a Heroku alternative? SnapDeploy offers the same ease-of-use at half the price. Automatic scaling, SSL, and zero DevOps. Start deploying in 60 seconds.")
                .keywords("heroku alternative, heroku alternative 2025, best heroku alternative, cheap heroku alternative, free heroku alternative, heroku competitor, heroku vs snapdeploy")
                .ogType("article")
                .build());
        
        // Railway Alternative
        pageConfigs.put("railway-alternative", PageSEO.builder()
                .title("Railway Alternative - SnapDeploy | Better Pricing & More Features")
                .description("SnapDeploy vs Railway: Get more features at better pricing. Deploy Docker containers with auto-scaling, custom domains, and instant SSL. Compare platforms now.")
                .keywords("railway alternative, railway vs snapdeploy, railway competitor, better than railway, railway pricing alternative")
                .ogType("article")
                .build());
        
        // Docker Hosting
        pageConfigs.put("docker-hosting", PageSEO.builder()
                .title("Docker Hosting Made Simple | Deploy Containers in 60 Seconds - SnapDeploy")
                .description("Simple Docker hosting for developers. Deploy containers without Kubernetes. Auto-scaling, SSL, and monitoring included. Start at $39/month. No DevOps required.")
                .keywords("docker hosting, docker container hosting, cheap docker hosting, simple docker hosting, managed docker hosting, docker cloud hosting, docker hosting service")
                .ogType("website")
                .build());
        
        // Container Hosting
        pageConfigs.put("container-hosting", PageSEO.builder()
                .title("Container Hosting Platform | Simple, Fast, Affordable - SnapDeploy")
                .description("Deploy containers without complexity. SnapDeploy handles scaling, SSL, and monitoring. Built on AWS with 99.9% uptime. Perfect for startups and agencies.")
                .keywords("container hosting, simple container hosting, cheap container hosting, container hosting platform, managed container hosting, docker container deployment")
                .ogType("website")
                .build());
        
        // For Startups
        pageConfigs.put("for-startups", PageSEO.builder()
                .title("Container Hosting for Startups | Deploy Your MVP Fast - SnapDeploy")
                .description("Launch your startup faster with SnapDeploy. Simple container hosting, predictable pricing, and zero DevOps. Deploy your MVP in 60 seconds. Trusted by 500+ startups.")
                .keywords("startup hosting, container hosting for startups, hosting for mvp launch, saas hosting for small teams, startup friendly hosting, paas for startups")
                .ogType("website")
                .build());
        
        // For Indie Developers
        pageConfigs.put("for-indie-developers", PageSEO.builder()
                .title("Best Hosting for Indie Developers | Simple Docker Deployment - SnapDeploy")
                .description("Perfect for indie hackers and side projects. Deploy containers easily, scale automatically, and pay only for what you use. Join 1000+ indie developers on SnapDeploy.")
                .keywords("indie developer hosting, best hosting for indie developers, hosting for side projects, freelancer web app hosting, developer side project hosting")
                .ogType("website")
                .build());
        
        // Pricing
        pageConfigs.put("pricing", PageSEO.builder()
                .title("Pricing - SnapDeploy | 50% Cheaper Than Heroku, No Hidden Fees")
                .description("Transparent container hosting pricing. Start at $39/month with 2GB RAM. No surprise bills. Compare our pricing with Heroku, Railway, and Render.")
                .keywords("docker hosting pricing, cheap docker hosting, container hosting pricing, heroku pricing alternative, affordable cloud hosting")
                .ogType("website")
                .build());
        
        // Login
        pageConfigs.put("login", PageSEO.builder()
                .title("Login - SnapDeploy | Deploy Docker Containers Instantly")
                .description("Sign in to SnapDeploy to manage your containers, monitor performance, and scale your applications.")
                .keywords("snapdeploy login, container hosting login")
                .noIndex(true)
                .build());
        
        // Register
        pageConfigs.put("register", PageSEO.builder()
                .title("Sign Up Free - SnapDeploy | Start Deploying in 60 Seconds")
                .description("Create your free SnapDeploy account. Get 200 free hours to deploy and test your applications. No credit card required.")
                .keywords("snapdeploy signup, free container hosting, docker hosting free tier")
                .build());
    }
    
    public PageSEO getPageSEO(String pageKey) {
        return pageConfigs.getOrDefault(pageKey, getDefaultSEO());
    }
    
    private PageSEO getDefaultSEO() {
        return PageSEO.builder()
                .title("SnapDeploy - Simple Container Hosting for Developers")
                .description("Deploy Docker containers instantly. Simple, fast, and affordable container hosting for developers.")
                .keywords("container hosting, docker hosting, paas platform")
                .build();
    }
    
    public String getStructuredData() {
        return """
                {
                  "@context": "https://schema.org",
                  "@type": "SoftwareApplication",
                  "name": "SnapDeploy",
                  "applicationCategory": "DeveloperApplication",
                  "operatingSystem": "Cloud",
                  "offers": {
                    "@type": "Offer",
                    "price": "39.00",
                    "priceCurrency": "USD",
                    "priceValidUntil": "2025-12-31"
                  },
                  "aggregateRating": {
                    "@type": "AggregateRating",
                    "ratingValue": "4.8",
                    "ratingCount": "156"
                  },
                  "description": "Simple container hosting platform for developers. Deploy Docker containers in 60 seconds with automatic scaling and SSL."
                }
                """;
    }
}

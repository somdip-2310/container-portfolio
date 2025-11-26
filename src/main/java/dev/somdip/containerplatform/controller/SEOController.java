package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.config.seo.PageSEO;
import dev.somdip.containerplatform.config.seo.SEOConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for SEO-optimized landing pages
 */
@Controller
public class SEOController {
    
    private final SEOConfig seoConfig;
    
    public SEOController(SEOConfig seoConfig) {
        this.seoConfig = seoConfig;
    }
    
    @GetMapping("/heroku-alternative")
    public String herokuAlternative(Model model) {
        PageSEO seo = seoConfig.getPageSEO("heroku-alternative");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/heroku-alternative";
    }
    
    @GetMapping("/railway-alternative")
    public String railwayAlternative(Model model) {
        PageSEO seo = seoConfig.getPageSEO("railway-alternative");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/railway-alternative";
    }
    
    @GetMapping("/render-alternative")
    public String renderAlternative(Model model) {
        PageSEO seo = seoConfig.getPageSEO("render-alternative");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/render-alternative";
    }
    
    @GetMapping("/docker-hosting")
    public String dockerHosting(Model model) {
        PageSEO seo = seoConfig.getPageSEO("docker-hosting");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/docker-hosting";
    }
    
    @GetMapping("/container-hosting")
    public String containerHosting(Model model) {
        PageSEO seo = seoConfig.getPageSEO("container-hosting");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/container-hosting";
    }
    
    @GetMapping("/for/startups")
    public String forStartups(Model model) {
        PageSEO seo = seoConfig.getPageSEO("for-startups");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/for-startups";
    }
    
    @GetMapping("/for/indie-developers")
    public String forIndieDevelopers(Model model) {
        PageSEO seo = seoConfig.getPageSEO("for-indie-developers");
        model.addAttribute("seo", seo);
        model.addAttribute("structuredData", seoConfig.getStructuredData());
        return "seo/for-indie-developers";
    }
    
    @GetMapping("/sitemap.xml")
    public String sitemap() {
        return "sitemap";
    }
    
    @GetMapping("/robots.txt")
    public String robots() {
        return "robots";
    }
}

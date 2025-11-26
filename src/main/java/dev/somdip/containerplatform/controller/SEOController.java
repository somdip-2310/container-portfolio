package dev.somdip.containerplatform.controller;

import dev.somdip.containerplatform.config.seo.PageSEO;
import dev.somdip.containerplatform.config.seo.SEOConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    @GetMapping("/pricing")
    public String pricing() {
        return "seo/pricing";
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/sitemap.xml");
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(content);
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots(HttpServletRequest request) throws IOException {
        // Get the server name from the request
        String serverName = request.getServerName();

        // If it's the development domain, serve robots-dev.txt (blocks all crawling)
        // Otherwise serve the production robots.txt (allows crawling)
        String robotsFile = serverName.contains("containers.somdip.dev")
            ? "templates/robots-dev.txt"
            : "templates/robots.txt";

        ClassPathResource resource = new ClassPathResource(robotsFile);
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(content);
    }
}

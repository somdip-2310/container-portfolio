package dev.somdip.containerplatform.service.php;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates nginx configuration for PHP applications
 */
@Slf4j
@Service
public class PHPNginxConfigGenerator {

    /**
     * Generate nginx configuration files
     */
    public void generateNginxConfig(Path sourceDirectory, PHPApplicationInfo appInfo) throws IOException {
        log.info("Generating nginx configuration for {}", appInfo.getFramework().getDisplayName());

        // Generate main nginx.conf
        String nginxConf = generateMainNginxConf();
        Files.writeString(sourceDirectory.resolve("nginx.conf"), nginxConf);

        // Generate site-specific default.conf
        String defaultConf = generateDefaultConf(appInfo);
        Files.writeString(sourceDirectory.resolve("default.conf"), defaultConf);

        log.info("Nginx configuration files generated");
    }

    private String generateMainNginxConf() {
        return "user www-data;\n" +
                "worker_processes auto;\n" +
                "pid /run/nginx.pid;\n" +
                "error_log /var/log/nginx/error.log;\n\n" +
                "events {\n" +
                "    worker_connections 1024;\n" +
                "}\n\n" +
                "http {\n" +
                "    include /etc/nginx/mime.types;\n" +
                "    default_type application/octet-stream;\n\n" +
                "    log_format main '$remote_addr - $remote_user [$time_local] \"$request\" '\n" +
                "                    '$status $body_bytes_sent \"$http_referer\" '\n" +
                "                    '\"$http_user_agent\" \"$http_x_forwarded_for\"';\n\n" +
                "    access_log /var/log/nginx/access.log main;\n\n" +
                "    sendfile on;\n" +
                "    tcp_nopush on;\n" +
                "    tcp_nodelay on;\n" +
                "    keepalive_timeout 65;\n" +
                "    types_hash_max_size 2048;\n\n" +
                "    # Gzip compression\n" +
                "    gzip on;\n" +
                "    gzip_vary on;\n" +
                "    gzip_proxied any;\n" +
                "    gzip_comp_level 6;\n" +
                "    gzip_types text/plain text/css text/xml text/javascript application/json application/javascript application/xml+rss application/rss+xml font/truetype font/opentype application/vnd.ms-fontobject image/svg+xml;\n\n" +
                "    # Include virtual host configs\n" +
                "    include /etc/nginx/conf.d/*.conf;\n" +
                "}\n";
    }

    private String generateDefaultConf(PHPApplicationInfo appInfo) {
        StringBuilder conf = new StringBuilder();

        conf.append("server {\n");
        conf.append("    listen 8000;\n");
        conf.append("    server_name _;\n\n");

        conf.append("    root ").append(appInfo.getDocumentRoot()).append(";\n");
        conf.append("    index index.php index.html index.htm;\n\n");

        conf.append("    # Logging\n");
        conf.append("    access_log /var/log/nginx/access.log;\n");
        conf.append("    error_log /var/log/nginx/error.log;\n\n");

        conf.append("    # Security headers\n");
        conf.append("    add_header X-Frame-Options \"SAMEORIGIN\" always;\n");
        conf.append("    add_header X-Content-Type-Options \"nosniff\" always;\n");
        conf.append("    add_header X-XSS-Protection \"1; mode=block\" always;\n\n");

        // Framework-specific configuration
        switch (appInfo.getFramework()) {
            case LARAVEL:
                conf.append("    # Laravel specific configuration\n");
                conf.append("    location / {\n");
                conf.append("        try_files $uri $uri/ /index.php?$query_string;\n");
                conf.append("    }\n\n");
                conf.append("    # Deny access to sensitive files\n");
                conf.append("    location ~ /\\.env {\n");
                conf.append("        deny all;\n");
                conf.append("    }\n\n");
                break;

            case SYMFONY:
                conf.append("    # Symfony specific configuration\n");
                conf.append("    location / {\n");
                conf.append("        try_files $uri /index.php$is_args$args;\n");
                conf.append("    }\n\n");
                break;

            case WORDPRESS:
                conf.append("    # WordPress specific configuration\n");
                conf.append("    location / {\n");
                conf.append("        try_files $uri $uri/ /index.php?$args;\n");
                conf.append("    }\n\n");
                conf.append("    # WordPress permalinks\n");
                conf.append("    location ~ \\.php$ {\n");
                conf.append("        include fastcgi_params;\n");
                conf.append("        fastcgi_pass 127.0.0.1:9000;\n");
                conf.append("        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;\n");
                conf.append("        fastcgi_index index.php;\n");
                conf.append("    }\n\n");
                break;

            default:
                conf.append("    # Generic PHP configuration\n");
                conf.append("    location / {\n");
                conf.append("        try_files $uri $uri/ /index.php?$query_string;\n");
                conf.append("    }\n\n");
                break;
        }

        // Common PHP handler (if not already defined for WordPress)
        if (appInfo.getFramework() != PHPFramework.WORDPRESS) {
            conf.append("    # PHP-FPM configuration\n");
            conf.append("    location ~ \\.php$ {\n");
            conf.append("        try_files $uri =404;\n");
            conf.append("        fastcgi_split_path_info ^(.+\\.php)(/.+)$;\n");
            conf.append("        fastcgi_pass 127.0.0.1:9000;\n");
            conf.append("        fastcgi_index index.php;\n");
            conf.append("        include fastcgi_params;\n");
            conf.append("        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;\n");
            conf.append("        fastcgi_param PATH_INFO $fastcgi_path_info;\n");
            conf.append("        fastcgi_buffering off;\n");
            conf.append("    }\n\n");
        }

        // Health check endpoint
        conf.append("    # Health check endpoint\n");
        conf.append("    location /health {\n");
        conf.append("        access_log off;\n");
        conf.append("        return 200 \"healthy\\n\";\n");
        conf.append("        add_header Content-Type text/plain;\n");
        conf.append("    }\n\n");

        // Deny access to hidden files
        conf.append("    # Deny access to hidden files\n");
        conf.append("    location ~ /\\. {\n");
        conf.append("        deny all;\n");
        conf.append("    }\n\n");

        // Static file handling
        conf.append("    # Static files\n");
        conf.append("    location ~* \\.(jpg|jpeg|gif|png|css|js|ico|xml|svg|woff|woff2|ttf|eot)$ {\n");
        conf.append("        expires 30d;\n");
        conf.append("        add_header Cache-Control \"public, immutable\";\n");
        conf.append("    }\n");

        conf.append("}\n");

        return conf.toString();
    }
}

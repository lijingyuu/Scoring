package com.scoring.backend.config;

import com.scoring.backend.security.AuthInterceptor;
import com.scoring.backend.security.RequestRateLimitInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.List;

@Configuration
@EnableConfigurationProperties({
        AuthProperties.class,
        WechatProperties.class,
        RateLimitProperties.class,
        CorsProperties.class,
        UploadProperties.class
})
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RequestRateLimitInterceptor requestRateLimitInterceptor;
    private final CorsProperties corsProperties;
    private final UploadProperties uploadProperties;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        RequestRateLimitInterceptor requestRateLimitInterceptor,
                        CorsProperties corsProperties,
                        UploadProperties uploadProperties) {
        this.authInterceptor = authInterceptor;
        this.requestRateLimitInterceptor = requestRateLimitInterceptor;
        this.corsProperties = corsProperties;
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestRateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .toList();
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods(corsProperties.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(corsProperties.getAllowedHeaders().toArray(String[]::new))
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDirectory = Path.of(uploadProperties.getDirectory())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDirectory);
    }
}

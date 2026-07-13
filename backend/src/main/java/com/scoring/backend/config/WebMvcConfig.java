package com.scoring.backend.config;

import com.scoring.backend.security.AuthInterceptor;
import com.scoring.backend.security.RequestRateLimitInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, WechatProperties.class, RateLimitProperties.class, CorsProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RequestRateLimitInterceptor requestRateLimitInterceptor;
    private final CorsProperties corsProperties;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        RequestRateLimitInterceptor requestRateLimitInterceptor,
                        CorsProperties corsProperties) {
        this.authInterceptor = authInterceptor;
        this.requestRateLimitInterceptor = requestRateLimitInterceptor;
        this.corsProperties = corsProperties;
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
}

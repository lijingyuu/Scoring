package com.scoring.backend.config;

import com.scoring.backend.security.AuthInterceptor;
import com.scoring.backend.security.RequestRateLimitInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, WechatProperties.class, RateLimitProperties.class})
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RequestRateLimitInterceptor requestRateLimitInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        RequestRateLimitInterceptor requestRateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.requestRateLimitInterceptor = requestRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestRateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}

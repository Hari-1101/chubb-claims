package com.chubb.claims.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Wires fake header auth and CORS for the /api endpoints. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final IdentityInterceptor identityInterceptor;
    private final String allowedOrigin;

    public WebConfig(IdentityInterceptor identityInterceptor,
                     @Value("${claims.cors.allowed-origin}") String allowedOrigin) {
        this.identityInterceptor = identityInterceptor;
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(identityInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", IdentityInterceptor.USER_ID_HEADER, IdentityInterceptor.USER_ROLE_HEADER)
                .exposedHeaders("Location");
    }
}

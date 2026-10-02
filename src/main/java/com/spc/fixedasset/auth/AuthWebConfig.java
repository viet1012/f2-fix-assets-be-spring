package com.spc.fixedasset.auth;

import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Login required on /api/** except health and auth; session cookie HttpOnly + SameSite=Lax, 8h timeout (set in code). */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/**");
    }

    /** Runs after the properties customizer, so these values win over server.servlet.session.* in application.yml. */
    @Bean
    public WebServerFactoryCustomizer<AbstractServletWebServerFactory> sessionCookieCustomizer() {
        return factory -> {
            factory.getSession().setTimeout(AuthSession.TIMEOUT);
            factory.getSession().getCookie().setHttpOnly(true);
            factory.getSession().getCookie().setSameSite(SameSite.LAX);
        };
    }
}

package com.example.patientvault.shared;

import java.util.Locale;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * UI language: English by default with a complete Greek translation. The choice is kept in a
 * first-party cookie that contains only the language code; {@code ?lang=el|en} switches it.
 */
@Configuration(proxyBeanMethods = false)
class I18nConfig implements WebMvcConfigurer {

    static final Set<String> SUPPORTED = Set.of("en", "el");

    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("pv_lang");
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setCookieHttpOnly(true);
        resolver.setCookieSameSite("Strict");
        resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LanguageInterceptor(localeResolver()));
    }

    /** Applies {@code ?lang=} only for supported languages; anything else is ignored. */
    static final class LanguageInterceptor implements HandlerInterceptor {

        private final LocaleResolver resolver;

        LanguageInterceptor(LocaleResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String lang = request.getParameter("lang");
            if (lang != null && SUPPORTED.contains(lang)) {
                resolver.setLocale(request, response, Locale.of(lang));
            }
            return true;
        }
    }
}

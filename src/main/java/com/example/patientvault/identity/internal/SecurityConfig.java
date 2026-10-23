package com.example.patientvault.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import com.example.patientvault.audit.AuditService;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    /** Spec 7.2: no inline script/style, same-origin everything, no framing, no plugins. */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data: blob:; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY = "accelerometer=(), autoplay=(), camera=(), display-capture=(), "
            + "geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), "
            + "publickey-credentials-get=(), usb=(), interest-cohort=()";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuditService audit, UserAccountService accounts)
            throws Exception {

        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        // Only remember plain page navigations; never an htmx partial or a non-GET request.
        requestCache.setRequestMatcher(request -> HttpMethod.GET.matches(request.getMethod())
                && !LoginRedirects.isHtmx(request)
                && !request.getRequestURI().startsWith("/actuator"));

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/icons/**", "/webjars/**", "/manifest.webmanifest").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .requestCache(cache -> cache.requestCache(requestCache))
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new LoginHandlers.Success(accounts, audit))
                .failureHandler(LoginHandlers.failure(audit)))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .deleteCookies("pv_session")
                .logoutSuccessHandler(LoginHandlers.logout(audit)))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new AppAuthenticationEntryPoint(audit))
                .accessDeniedHandler(new AppAccessDeniedHandler(audit)))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny));

        return http.build();
    }
}

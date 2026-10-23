package com.example.patientvault.identity.internal;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

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

    /** Remembers the page a user wanted before login. Only HTML page navigations: never htmx partials, assets, favicons or non-GET. */
    @Bean
    RequestCache requestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        requestCache.setRequestMatcher(request -> HttpMethod.GET.matches(request.getMethod())
                && !LoginRedirects.isHtmx(request)
                && acceptsHtml(request.getHeader("Accept"))
                && !request.getRequestURI().startsWith("/actuator"));
        return requestCache;
    }

    private static boolean acceptsHtml(String accept) {
        return accept != null && accept.contains("text/html");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuditService audit, RateLimitProperties rateLimit,
                                            MfaProperties mfa, LoginCompletion loginCompletion, Clock clock,
                                            RequestCache requestCache,
                                            @Value("${app.security.session.absolute-timeout}") Duration absoluteTimeout)
            throws Exception {
        http
            .addFilterBefore(new AuthRateLimitFilter(rateLimit, audit), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new AbsoluteSessionTimeoutFilter(absoluteTimeout, clock, audit),
                    SecurityContextHolderFilter.class)
            .addFilterAfter(new MustChangePasswordFilter(), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login/mfa", "/login/mfa/**", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/icons/**", "/webjars/**", "/manifest.webmanifest").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .requestCache(cache -> cache.requestCache(requestCache))
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new LoginHandlers.PasswordStep(mfa, loginCompletion, clock))
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

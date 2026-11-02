package vn.sportstore.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import vn.sportstore.model.UserAccount;
import vn.sportstore.repository.UserAccountRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    ApplicationRunner demoUsers(UserAccountRepository users, PasswordEncoder encoder,
            @Value("${sportstore.bootstrap-admin-username:admin}") String adminUsername,
            @Value("${sportstore.bootstrap-admin-password:Admin@123}") String adminPassword,
            @Value("${sportstore.bootstrap-staff-username:staff}") String staffUsername,
            @Value("${sportstore.bootstrap-staff-password:Staff@123}") String staffPassword) {
        return args -> {
            if (users.count() == 0) {
                users.saveAll(java.util.List.of(
                        new UserAccount(adminUsername, encoder.encode(adminPassword), "ADMIN", true),
                        new UserAccount(staffUsername, encoder.encode(staffPassword), "STAFF", true)));
            }
        };
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        http
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                    // CookieCsrfTokenRepository and this handler use the same raw token in X-XSRF-TOKEN.
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(context -> context.securityContextRepository(securityContextRepository()))
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/index.html", "/api/health", "/api/auth/csrf", "/api/auth/login").permitAll()
                    .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/checkout").hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers("/api/**").hasRole("ADMIN")
                    .anyRequest().permitAll())
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) -> writeError(response, 401, "Vui lòng đăng nhập"))
                    .accessDeniedHandler((request, response, exception) -> writeError(response, 403, "Bạn không có quyền thực hiện thao tác này")))
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(options -> {})
                    .referrerPolicy(policy -> policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}

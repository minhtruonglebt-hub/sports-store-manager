package vn.sportstore.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body.username() == null || body.password() == null || body.username().length() > 80 || body.password().length() > 200)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Thông tin đăng nhập không hợp lệ");
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            return userInfo(authentication);
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng");
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) { return userInfo(authentication); }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request, response, null);
        return Map.of("message", "Đã đăng xuất");
    }

    private Map<String, Object> userInfo(Authentication authentication) {
        return Map.of("username", authentication.getName(), "roles", authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "")).toList());
    }

    public record LoginRequest(String username, String password) {}
}

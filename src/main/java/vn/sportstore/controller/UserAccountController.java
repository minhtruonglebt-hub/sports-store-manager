package vn.sportstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.service.UserAccountService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAccountController {
    private final UserAccountService users;

    public UserAccountController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserAccountService.UserView> all() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountService.UserView create(@RequestBody CreateUserRequest request) {
        if (request == null) throw CrudControllerSupport.bad("Thông tin tài khoản không hợp lệ");
        return users.create(request.username(), request.password(), request.role());
    }

    @PutMapping("/{id}")
    public UserAccountService.UserView update(@PathVariable Long id, @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        if (request == null) throw CrudControllerSupport.bad("Thông tin tài khoản không hợp lệ");
        UserAccountService.UserView current = users.list().stream()
                .filter(account -> account.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (current.username().equalsIgnoreCase(authentication.getName())
                && (!"ADMIN".equalsIgnoreCase(request.role()) || !Boolean.TRUE.equals(request.enabled())))
            throw CrudControllerSupport.conflict("Không thể thay đổi vai trò hoặc vô hiệu hóa tài khoản đang đăng nhập");
        return users.update(id, request.password(), request.role(), request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        users.delete(id, authentication.getName());
    }

    public record CreateUserRequest(String username, String password, String role) {}
    public record UpdateUserRequest(String password, String role, Boolean enabled) {}
}

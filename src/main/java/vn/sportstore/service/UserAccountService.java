package vn.sportstore.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.sportstore.model.UserAccount;
import vn.sportstore.repository.UserAccountRepository;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserAccountService implements UserDetailsService {
    private static final Set<String> ROLES = Set.of("ADMIN", "STAFF");
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRole())
                .disabled(!account.isEnabled())
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll().stream()
                .sorted((first, second) -> first.getUsername().compareToIgnoreCase(second.getUsername()))
                .map(UserAccountService::view)
                .toList();
    }

    @Transactional
    public UserView create(String username, String password, String role) {
        String normalizedUsername = validateUsername(username);
        validatePassword(password);
        String normalizedRole = validateRole(role);
        if (users.findByUsernameIgnoreCase(normalizedUsername).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        UserAccount account = new UserAccount(normalizedUsername, passwordEncoder.encode(password), normalizedRole, true);
        return view(users.save(account));
    }

    @Transactional
    public UserView update(Long id, String password, String role, Boolean enabled) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        String normalizedRole = validateRole(role);
        if (enabled == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái tài khoản không hợp lệ");
        if (password != null && !password.isBlank()) {
            validatePassword(password);
            account.setPasswordHash(passwordEncoder.encode(password));
        }
        protectLastAdmin(account, normalizedRole, enabled);
        account.setRole(normalizedRole);
        account.setEnabled(enabled);
        return view(users.save(account));
    }

    @Transactional
    public void delete(Long id, String actorUsername) {
        UserAccount account = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (account.getUsername().equalsIgnoreCase(actorUsername))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa tài khoản đang đăng nhập");
        protectLastAdmin(account, null, false);
        users.delete(account);
    }

    private void protectLastAdmin(UserAccount account, String newRole, boolean enabled) {
        boolean removesAdmin = "ADMIN".equals(account.getRole())
                && account.isEnabled()
                && (!"ADMIN".equals(newRole) || !enabled);
        if (removesAdmin && users.countByRoleAndEnabledTrue("ADMIN") <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hệ thống phải còn ít nhất một quản trị viên đang hoạt động");
    }

    private static String validateUsername(String username) {
        if (username == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập không hợp lệ");
        String normalized = username.trim();
        if (!normalized.matches("[A-Za-z0-9._-]{3,40}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập phải dài 3–40 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang");
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải dài từ 12 đến 200 ký tự");
    }

    private static String validateRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò chỉ được là ADMIN hoặc STAFF");
        return normalized;
    }

    private static UserView view(UserAccount account) {
        return new UserView(account.getId(), account.getUsername(), account.getRole(), account.isEnabled());
    }

    public record UserView(Long id, String username, String role, boolean enabled) {}
}

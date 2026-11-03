package vn.sportstore.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.sportstore.model.Customer;
import vn.sportstore.model.UserAccount;
import vn.sportstore.repository.CustomerRepository;
import vn.sportstore.repository.UserAccountRepository;

import java.nio.charset.StandardCharsets;

@Service
public class CustomerAccountService {
    private final CustomerRepository customers;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public CustomerAccountService(CustomerRepository customers, UserAccountRepository users,
            PasswordEncoder passwordEncoder) {
        this.customers = customers;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegistrationResult register(RegistrationRequest request) {
        if (request == null) throw bad("Thông tin đăng ký không hợp lệ");
        String username = request.username() == null ? "" : request.username().trim();
        if (!username.matches("[A-Za-z0-9._-]{3,40}"))
            throw bad("Tên đăng nhập phải dài 3–40 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang");
        if (request.password() == null || request.password().length() < 12
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw bad("Mật khẩu phải dài ít nhất 12 ký tự và tối đa 72 byte UTF-8");
        if (users.findByUsernameIgnoreCase(username).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");

        String fullName = request.fullName() == null ? "" : request.fullName().trim();
        String email = request.email() == null ? "" : request.email().trim();
        String phone = request.phone() == null ? "" : request.phone().trim();
        if (fullName.isBlank() || fullName.length() > 150) throw bad("Họ tên không được để trống và tối đa 150 ký tự");
        if (email.length() > 160 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw bad("Email không đúng định dạng");
        if (!phone.isEmpty() && !phone.matches("^[0-9+() .-]{8,20}$"))
            throw bad("Số điện thoại không đúng định dạng");

        Customer customer = customers.save(new Customer(fullName, phone, email, null));
        UserAccount account = new UserAccount(username, passwordEncoder.encode(request.password()), "CUSTOMER", true);
        account.setCustomer(customer);
        users.save(account);
        return new RegistrationResult(username);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record RegistrationRequest(String username, String password, String fullName, String email, String phone) {}
    public record RegistrationResult(String username) {}
}

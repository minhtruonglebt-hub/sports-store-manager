package vn.sportstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_accounts")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserAccount extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    public UserAccount(String username, String passwordHash, String role, boolean enabled) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }
}

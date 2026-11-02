package vn.sportstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.UserAccount;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    long countByRoleAndEnabledTrue(String role);
}

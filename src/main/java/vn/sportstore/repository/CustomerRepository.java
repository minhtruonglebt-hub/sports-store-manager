package vn.sportstore.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.Customer;
public interface CustomerRepository extends JpaRepository<Customer, Long> {}

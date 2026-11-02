package vn.sportstore.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.CustomerOrder;
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {}

package vn.sportstore.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.CustomerOrder;
import java.util.List;
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findAllByCustomer_IdOrderByOrderDateDesc(Long customerId);
}

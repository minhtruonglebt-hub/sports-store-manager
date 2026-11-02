package vn.sportstore.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.OrderDetail;
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {}

package vn.sportstore.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportstore.model.CustomerOrder;
import java.util.List;
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findAllByCustomer_IdOrderByOrderDateDesc(Long customerId);

    @Query("""
            select o from CustomerOrder o join o.customer c
            where (:id is not null and o.id = :id) or :query = '' or lower(concat(coalesce(o.status, ''), ' ',
                    coalesce(o.shippingAddress, ''), ' ', coalesce(o.paymentMethod, ''), ' ',
                    coalesce(o.paymentStatus, ''), ' ', coalesce(c.fullName, ''), ' ',
                    coalesce(c.email, ''), ' ', coalesce(c.phone, '')))
                    like concat('%', :query, '%')
            """)
    Page<CustomerOrder> search(@Param("query") String query, @Param("id") Long id, Pageable pageable);
}

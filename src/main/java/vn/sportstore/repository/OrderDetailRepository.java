package vn.sportstore.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportstore.model.OrderDetail;
import java.util.List;
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    List<OrderDetail> findAllByOrder_Id(Long orderId);

    @Query("""
            select d from OrderDetail d join d.order o join d.product p
            where (:id is not null and (d.id = :id or o.id = :id or p.id = :id))
                or :query = '' or lower(concat(coalesce(p.name, ''), ' ',
                    coalesce(p.brand, ''), ' ', coalesce(o.status, '')))
                    like concat('%', :query, '%')
            """)
    Page<OrderDetail> search(@Param("query") String query, @Param("id") Long id, Pageable pageable);
}

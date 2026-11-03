package vn.sportstore.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportstore.model.Product;
import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
            select p from Product p
            where (:query = '' or lower(concat(coalesce(p.name, ''), ' ', coalesce(p.brand, ''), ' ', coalesce(p.color, '')))
                    like concat('%', :query, '%'))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:status = '' or p.status = :status)
            """)
    Page<Product> search(@Param("query") String query, @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice,
            @Param("status") String status, Pageable pageable);
}

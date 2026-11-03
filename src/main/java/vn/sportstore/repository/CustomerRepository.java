package vn.sportstore.repository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportstore.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("""
            select c from Customer c
            where (:id is not null and c.id = :id) or :query = '' or lower(concat(coalesce(c.fullName, ''), ' ',
                    coalesce(c.email, ''), ' ', coalesce(c.phone, ''), ' ', coalesce(c.address, '')))
                    like concat('%', :query, '%')
            """)
    Page<Customer> search(@Param("query") String query, @Param("id") Long id, Pageable pageable);
}

package vn.sportstore.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.Product;
public interface ProductRepository extends JpaRepository<Product, Long> {}

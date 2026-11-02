package vn.sportstore.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.Category;
public interface CategoryRepository extends JpaRepository<Category, Long> {}

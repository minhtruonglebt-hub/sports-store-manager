package vn.sportstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.sportstore.model.StockMovement;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findTop100ByProduct_IdOrderByCreatedAtDescIdDesc(Long productId);
    boolean existsByProduct_Id(Long productId);
}

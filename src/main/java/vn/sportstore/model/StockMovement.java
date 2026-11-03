package vn.sportstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_product_created", columnList = "product_id, created_at")
})
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StockMovement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Transient
    private Long productId;

    @Column(name = "movement_type", nullable = false, length = 30)
    private String movementType;

    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @Column(nullable = false, length = 250)
    private String reason;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getProductId() {
        return product != null ? product.getId() : productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public void setProduct(Product product) {
        this.product = product;
        this.productId = product == null ? null : product.getId();
    }
}

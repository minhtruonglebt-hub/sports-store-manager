package vn.sportstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "order_details")
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
public class OrderDetail extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore private CustomerOrder order;
    @Transient private Long orderId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore private Product product;
    @Transient private Long productId;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;

    public Long getOrderId() { return order != null ? order.getId() : orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setOrder(CustomerOrder order) { this.order = order; this.orderId = order == null ? null : order.getId(); }
    public Long getProductId() { return product != null ? product.getId() : productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setProduct(Product product) { this.product = product; this.productId = product == null ? null : product.getId(); }
}

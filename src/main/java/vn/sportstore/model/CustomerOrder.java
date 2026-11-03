package vn.sportstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "customer_orders")
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
public class CustomerOrder extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnore private Customer customer;
    @Transient private Long customerId;
    @Column(nullable = false) private LocalDateTime orderDate;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(length = 500) private String shippingAddress;
    @Column(length = 30) private String paymentMethod;
    @Column(length = 80) private String paymentStatus;
    @Column(name = "order_source", length = 20) private String orderSource = "STORE";

    public Long getCustomerId() { return customer != null ? customer.getId() : customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public void setCustomer(Customer customer) { this.customer = customer; this.customerId = customer == null ? null : customer.getId(); }
}

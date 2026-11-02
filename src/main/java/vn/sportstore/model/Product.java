package vn.sportstore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity @Table(name = "products")
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnore private Category category;
    @Transient private Long categoryId;
    @Column(nullable = false, length = 180) private String name;
    @Column(length = 100) private String brand;
    @Column(length = 30) private String size;
    @Column(length = 60) private String color;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal price;
    @Column(nullable = false) private Integer quantity;
    @Column(length = 30) private String status;
    @Column(length = 500) private String imageUrl;

    public Product(String name, Long categoryId, String brand, String size, String color, BigDecimal price, Integer quantity, String status) {
        this.name = name; this.categoryId = categoryId; this.brand = brand; this.size = size; this.color = color;
        this.price = price; this.quantity = quantity; this.status = status;
    }
    public Long getCategoryId() { return category != null ? category.getId() : categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public void setCategory(Category category) { this.category = category; this.categoryId = category == null ? null : category.getId(); }
}

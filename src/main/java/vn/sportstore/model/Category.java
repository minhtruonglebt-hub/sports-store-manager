package vn.sportstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity @Table(name = "categories")
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
public class Category extends BaseEntity {
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(length = 1000) private String description;
    public Category(String name, String description) { this.name = name; this.description = description; }
}

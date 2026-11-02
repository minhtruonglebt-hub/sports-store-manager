package vn.sportstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity @Table(name = "customers")
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
public class Customer extends BaseEntity {
    @Column(nullable = false, length = 150) private String fullName;
    @Column(length = 30) private String phone;
    @Column(length = 160) private String email;
    @Column(length = 500) private String address;
    public Customer(String fullName, String phone, String email, String address) {
        this.fullName = fullName; this.phone = phone; this.email = email; this.address = address;
    }
}

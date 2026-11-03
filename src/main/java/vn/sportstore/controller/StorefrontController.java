package vn.sportstore.controller;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sportstore.model.OrderDetail;
import vn.sportstore.model.Product;
import vn.sportstore.model.UserAccount;
import vn.sportstore.repository.OrderDetailRepository;
import vn.sportstore.repository.OrderRepository;
import vn.sportstore.repository.ProductRepository;
import vn.sportstore.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/store")
public class StorefrontController {
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserAccountRepository users;
    private final OrderDetailRepository orderDetails;

    public StorefrontController(ProductRepository products, OrderRepository orders, UserAccountRepository users,
            OrderDetailRepository orderDetails) {
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.orderDetails = orderDetails;
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductView> products() {
        return products.findAll().stream()
                .filter(product -> !"Ngừng bán".equals(product.getStatus()))
                .map(product -> new ProductView(product.getId(), product.getName(), product.getBrand(),
                        product.getSize(), product.getColor(), product.getPrice(), product.getQuantity(),
                        product.getImageUrl()))
                .toList();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public CustomerView me(Authentication authentication) {
        UserAccount account = customerAccount(authentication);
        var customer = account.getCustomer();
        return new CustomerView(account.getUsername(), customer.getFullName(), customer.getEmail(), customer.getPhone());
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<OrderView> orders(Authentication authentication) {
        Long customerId = customerAccount(authentication).getCustomer().getId();
        return orders.findAllByCustomer_IdOrderByOrderDateDesc(customerId).stream()
                .map(order -> new OrderView(order.getId(), order.getOrderDate(), order.getStatus(),
                        order.getTotalAmount(), order.getShippingAddress(), order.getPaymentMethod(),
                        order.getPaymentStatus(), orderDetails.findAllByOrder_Id(order.getId()).stream()
                                .map(this::detailView).toList()))
                .toList();
    }

    private UserAccount customerAccount(Authentication authentication) {
        UserAccount account = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại"));
        if (account.getCustomer() == null)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Tài khoản chưa liên kết hồ sơ khách hàng");
        return account;
    }

    private OrderItemView detailView(OrderDetail detail) {
        Product product = detail.getProduct();
        return new OrderItemView(product.getName(), product.getImageUrl(), detail.getQuantity(),
                detail.getUnitPrice(), detail.getSubtotal());
    }

    public record ProductView(Long id, String name, String brand, String size, String color,
            BigDecimal price, Integer quantity, String imageUrl) {}
    public record CustomerView(String username, String fullName, String email, String phone) {}
    public record OrderView(Long id, LocalDateTime orderDate, String status, BigDecimal totalAmount,
            String shippingAddress, String paymentMethod, String paymentStatus, List<OrderItemView> items) {}
    public record OrderItemView(String productName, String imageUrl, Integer quantity,
            BigDecimal unitPrice, BigDecimal subtotal) {}
}

package vn.sportstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.model.OrderDetail;
import vn.sportstore.model.Product;
import vn.sportstore.model.UserAccount;
import vn.sportstore.repository.UserAccountRepository;
import vn.sportstore.service.StoreService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private static final Set<String> METHODS = Set.of("COD", "BANK_TRANSFER", "MOMO_DEMO", "CARD_DEMO");
    private final StoreService store;
    private final UserAccountRepository users;

    public CheckoutController(StoreService store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> checkout(@RequestBody CheckoutRequest request) {
        synchronized (store) {
            return checkoutLocked(request, false);
        }
    }

    @PostMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> customerCheckout(@RequestBody CheckoutRequest request, Authentication authentication) {
        if (request == null) throw bad("Đơn hàng không hợp lệ");
        UserAccount account = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> bad("Tài khoản khách hàng không hợp lệ"));
        if (account.getCustomer() == null) throw bad("Tài khoản chưa liên kết hồ sơ khách hàng");
        if (request.shippingAddress() == null || request.shippingAddress().isBlank()
                || request.shippingAddress().trim().length() > 500)
            throw bad("Vui lòng nhập địa chỉ giao hàng (tối đa 500 ký tự)");
        CheckoutRequest customerRequest = new CheckoutRequest(account.getCustomer().getId(),
                request.shippingAddress(), request.paymentMethod(), request.items());
        synchronized (store) {
            return checkoutLocked(customerRequest, true);
        }
    }

    private Map<String, Object> checkoutLocked(CheckoutRequest request, boolean onlineOrder) {
        if (request == null || request.customerId() == null || store.customers().findById(request.customerId()).isEmpty())
            throw bad("Vui lòng chọn khách hàng hợp lệ");
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 50)
            throw bad("Đơn hàng cần có từ 1 đến 50 mặt hàng");
        String method = request.paymentMethod() == null ? "COD" : request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw bad("Phương thức thanh toán không được hỗ trợ");

        // Validate the whole cart before changing stock, and merge duplicate product rows.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CheckoutItem item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() < 1 || item.quantity() > 999)
                throw bad("Sản phẩm hoặc số lượng không hợp lệ");
            quantities.merge(item.productId(), item.quantity(), Math::addExact);
        }
        Map<Long, Product> selected = new LinkedHashMap<>();
        for (var entry : quantities.entrySet()) {
            Product product = store.products().findById(entry.getKey())
                    .orElseThrow(() -> bad("Có sản phẩm không còn tồn tại"));
            if ("Ngừng bán".equals(product.getStatus())) throw bad("Sản phẩm " + product.getName() + " đã ngừng bán");
            if (product.getQuantity() == null || product.getQuantity() < entry.getValue())
                throw bad("Không đủ tồn kho cho sản phẩm " + product.getName());
            if (product.getPrice() == null || product.getPrice().signum() < 0) throw bad("Giá sản phẩm không hợp lệ");
            selected.put(product.getId(), product);
        }

        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(request.customerId());
        order.setCustomer(store.customers().findById(request.customerId()).orElseThrow());
        order.setOrderDate(LocalDateTime.now().withNano(0));
        order.setStatus("Chờ xác nhận");
        order.setShippingAddress(request.shippingAddress() == null || request.shippingAddress().isBlank()
                ? store.customers().findById(request.customerId()).orElseThrow().getAddress()
                : request.shippingAddress().trim());
        order.setPaymentMethod(method);
        order.setOrderSource(onlineOrder ? "ONLINE" : "STORE");
        order.setPaymentStatus(switch (method) {
            case "COD" -> "Chờ thu tiền khi giao hàng";
            case "BANK_TRANSFER" -> "Chờ xác nhận chuyển khoản";
            default -> "Đã thanh toán (MÔ PHỎNG)";
        });
        order.setTotalAmount(BigDecimal.ZERO);
        store.orders().save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (var entry : quantities.entrySet()) {
            Product product = selected.get(entry.getKey());
            int quantity = entry.getValue();
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            OrderDetail detail = new OrderDetail();
            detail.setOrderId(order.getId()); detail.setProductId(product.getId());
            detail.setOrder(order); detail.setProduct(product);
            detail.setQuantity(quantity); detail.setUnitPrice(product.getPrice()); detail.setSubtotal(subtotal);
            store.details().save(detail);
            product.setQuantity(product.getQuantity() - quantity);
            if (product.getQuantity() <= product.getMinStock() && !"Ngừng bán".equals(product.getStatus())) product.setStatus("Sắp hết");
            store.products().save(product);
            store.recordStockMovement(product, -quantity, "SALE", "Bán hàng đơn #" + order.getId(), order.getId());
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);
        store.orders().save(order);
        return Map.of("order", order, "items", store.detailsOfOrder(order.getId()),
                "paymentNotice", method.endsWith("_DEMO") ? "Giao dịch thanh toán giả lập để trình diễn; không có tiền thật được thu." : "");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record CheckoutRequest(Long customerId, String shippingAddress, String paymentMethod, List<CheckoutItem> items) {}
    public record CheckoutItem(Long productId, Integer quantity) {}
}

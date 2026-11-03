package vn.sportstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.model.OrderDetail;
import vn.sportstore.model.Product;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.repository.OrderDetailRepository;
import vn.sportstore.service.StoreService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;

@RestController @RequestMapping("/api/orders")
public class OrderController extends CrudControllerSupport<CustomerOrder> {
    private final StoreService store;
    private final OrderDetailRepository orderDetails;
    public OrderController(StoreService store, OrderDetailRepository orderDetails) {
        this.store = store;
        this.orderDetails = orderDetails;
    }
    @GetMapping public List<CustomerOrder> all() { return all(store.orders()); }
    @GetMapping("/online")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<OnlineOrderView> onlineOrders() {
        return store.orders().findAll().stream()
                .filter(order -> "ONLINE".equals(order.getOrderSource()))
                .sorted((first, second) -> second.getOrderDate().compareTo(first.getOrderDate()))
                .map(order -> {
                    var customer = order.getCustomer();
                    List<OnlineOrderItemView> items = orderDetails.findAllByOrder_Id(order.getId()).stream()
                            .map(this::onlineOrderItem).toList();
                    return new OnlineOrderView(order.getId(), order.getCustomerId(), customer.getFullName(),
                            order.getCustomerPhone() == null ? customer.getPhone() : order.getCustomerPhone(),
                            customer.getEmail(), order.getOrderDate(), order.getStatus(),
                            order.getTotalAmount(), order.getShippingAddress(), order.getPaymentMethod(),
                            order.getPaymentStatus(), order.getCustomerNote(), items);
                })
                .toList();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @org.springframework.transaction.annotation.Transactional
    public CustomerOrder updateStatus(@PathVariable Long id, @RequestBody StatusUpdate request) {
        synchronized (store) {
            CustomerOrder order = one(store.orders(), id);
            if (!"ONLINE".equals(order.getOrderSource()))
                throw bad("Đơn hàng này không phải đơn đặt từ cửa hàng trực tuyến");
            if (request == null || request.status() == null || !ORDER_STATUSES.contains(request.status()))
                throw bad("Trạng thái đơn hàng không hợp lệ");
            if ("Đã hủy".equals(order.getStatus()) && !"Đã hủy".equals(request.status()))
                throw bad("Đơn đã hủy không thể mở lại");
            if ("Đã hủy".equals(request.status())) ensureVnPayCanBeCancelled(order);
            String previousStatus = order.getStatus();
            order.setStatus(request.status());
            CustomerOrder saved = store.orders().save(order);
            if (!"Đã hủy".equals(previousStatus) && "Đã hủy".equals(saved.getStatus()))
                store.restoreStockForOrder(id);
            return saved;
        }
    }

    @PostMapping("/{id}/payment-confirmation")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @org.springframework.transaction.annotation.Transactional
    public CustomerOrder confirmPayment(@PathVariable Long id) {
        CustomerOrder order = one(store.orders(), id);
        if (!"ONLINE".equals(order.getOrderSource()))
            throw bad("Chỉ xác nhận thanh toán cho đơn đặt trực tuyến");
        if ("Đã hủy".equals(order.getStatus()))
            throw bad("Không thể xác nhận thanh toán cho đơn hàng đã hủy");
        String confirmedStatus = switch (order.getPaymentMethod()) {
            case "BANK_TRANSFER" -> "Đã xác nhận chuyển khoản";
            case "COD" -> {
                if (!"Hoàn thành".equals(order.getStatus()))
                    throw bad("Chỉ xác nhận tiền COD sau khi đơn hàng đã giao thành công");
                yield "Đã thu tiền khi giao hàng";
            }
            default -> throw bad("Phương thức thanh toán của đơn hàng không được hỗ trợ");
        };
        if (!confirmedStatus.equals(order.getPaymentStatus())) {
            order.setPaymentStatus(confirmedStatus);
            return store.orders().save(order);
        }
        return order;
    }

    @GetMapping("/{id}") public CustomerOrder one(@PathVariable Long id) { return one(store.orders(), id); }
    @PostMapping public CustomerOrder create(@RequestBody CustomerOrder row) { return create(store.orders(), row); }
    @PutMapping("/{id}") @org.springframework.transaction.annotation.Transactional
    public CustomerOrder update(@PathVariable Long id, @RequestBody CustomerOrder row) {
        synchronized (store) {
            CustomerOrder current = one(store.orders(), id);
            String oldStatus = current.getStatus();
            if (row == null) throw bad("Đơn hàng không hợp lệ");
            row.setId(id);
            if (!"Đã hủy".equals(oldStatus) && "Đã hủy".equals(row.getStatus()))
                ensureVnPayCanBeCancelled(current);
            row.setOrderSource(current.getOrderSource());
            row.setPaymentMethod(current.getPaymentMethod());
            row.setPaymentStatus(current.getPaymentStatus());
            validate(row);
            CustomerOrder saved = store.orders().save(row);
            if (!"Đã hủy".equals(oldStatus) && "Đã hủy".equals(saved.getStatus()))
                store.restoreStockForOrder(id);
            return saved;
        }
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.transaction.annotation.Transactional
    public void delete(@PathVariable Long id) {
        synchronized (store) {
            CustomerOrder order = one(store.orders(), id);
            if ("VNPAY".equals(order.getPaymentMethod())
                    && ("Chờ thanh toán VNPay".equals(order.getPaymentStatus())
                            || "Đã thanh toán VNPay".equals(order.getPaymentStatus())))
                throw bad("Không thể xóa đơn VNPay đang chờ hoặc đã thanh toán");
            remove(store.orders(), id);
        }
    }

    private static void ensureVnPayCanBeCancelled(CustomerOrder order) {
        if ("VNPAY".equals(order.getPaymentMethod())
                && "Chờ thanh toán VNPay".equals(order.getPaymentStatus()))
            throw bad("Đơn VNPay đang chờ kết quả thanh toán; không thể hủy lúc này");
    }

    @Override protected void validate(CustomerOrder order) {
        if (order.getCustomerId() == null || store.customers().findById(order.getCustomerId()).isEmpty()) throw bad("Khách hàng không tồn tại");
        order.setCustomer(store.customers().findById(order.getCustomerId()).orElseThrow());
        if (order.getId() != null) {
            CustomerOrder current = store.orders().findById(order.getId()).orElse(null);
            if (current != null && "Đã hủy".equals(current.getStatus()) && !"Đã hủy".equals(order.getStatus()))
                throw bad("Đơn đã hủy không thể mở lại; hãy tạo đơn mới");
        }
        if (order.getOrderDate() == null) order.setOrderDate(LocalDateTime.now().withNano(0));
        if (blank(order.getStatus())) order.setStatus("Chờ xác nhận");
        if (order.getId() != null && !store.detailsOfOrder(order.getId()).isEmpty()) order.setTotalAmount(store.totalOfOrder(order.getId()));
        else if (order.getTotalAmount() == null) order.setTotalAmount(BigDecimal.ZERO);
        if (order.getTotalAmount().signum() < 0) throw bad("Tổng tiền không được âm");
    }
    @Override protected void beforeDelete(CustomerOrder old) {
        if (!"Đã hủy".equals(old.getStatus())) store.restoreStockForOrder(old.getId());
        store.deleteDetailsOfOrder(old.getId());
    }

    private OnlineOrderItemView onlineOrderItem(OrderDetail detail) {
        Product product = detail.getProduct();
        return new OnlineOrderItemView(product.getName(), product.getBrand(), product.getSize(),
                detail.getQuantity(), detail.getUnitPrice(), detail.getSubtotal());
    }

    private static final Set<String> ORDER_STATUSES =
            Set.of("Chờ xác nhận", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy");

    public record StatusUpdate(String status) {}
    public record OnlineOrderItemView(String productName, String brand, String size,
            Integer quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
    public record OnlineOrderView(Long id, Long customerId, String customerName, String customerPhone,
            String customerEmail, LocalDateTime orderDate, String status, BigDecimal totalAmount,
            String shippingAddress, String paymentMethod, String paymentStatus, String customerNote,
            List<OnlineOrderItemView> items) {}
}

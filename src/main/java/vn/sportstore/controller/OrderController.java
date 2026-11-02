package vn.sportstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.service.StoreService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController @RequestMapping("/api/orders")
public class OrderController extends CrudControllerSupport<CustomerOrder> {
    private final StoreService store;
    public OrderController(StoreService store) { this.store = store; }
    @GetMapping public List<CustomerOrder> all() { return all(store.orders()); }
    @GetMapping("/{id}") public CustomerOrder one(@PathVariable Long id) { return one(store.orders(), id); }
    @PostMapping public CustomerOrder create(@RequestBody CustomerOrder row) { return create(store.orders(), row); }
    @PutMapping("/{id}") @org.springframework.transaction.annotation.Transactional
    public CustomerOrder update(@PathVariable Long id, @RequestBody CustomerOrder row) {
        synchronized (store) {
            CustomerOrder current = one(store.orders(), id);
            String oldStatus = current.getStatus();
            if (row == null) throw bad("Đơn hàng không hợp lệ");
            row.setId(id);
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
            remove(store.orders(), id);
        }
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
}

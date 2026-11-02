package vn.sportstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.model.OrderDetail;
import vn.sportstore.model.Product;
import vn.sportstore.service.StoreService;
import java.math.BigDecimal;
import java.util.List;

@RestController @RequestMapping("/api/order-details")
public class OrderDetailController extends CrudControllerSupport<OrderDetail> {
    private final StoreService store;
    public OrderDetailController(StoreService store) { this.store = store; }
    @GetMapping public List<OrderDetail> all() { return all(store.details()); }
    @GetMapping("/{id}") public OrderDetail one(@PathVariable Long id) { return one(store.details(), id); }
    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public OrderDetail create(@RequestBody OrderDetail row) {
        synchronized (store) {
            if (row == null) throw bad("Chi tiết đơn hàng không hợp lệ");
            row.setId(null);
            validate(row);
            ensureOrderCanBeEdited(row.getOrderId());
            adjustStock(null, row);
            OrderDetail saved = store.details().save(row);
            store.recalcOrderTotal(saved.getOrderId());
            return saved;
        }
    }

    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public OrderDetail update(@PathVariable Long id, @RequestBody OrderDetail row) {
        synchronized (store) {
            OrderDetail current = one(store.details(), id);
            Long oldOrderId = current.getOrderId();
            ensureOrderCanBeEdited(oldOrderId);
            if (row == null) throw bad("Chi tiết đơn hàng không hợp lệ");

            row.setId(id);
            validate(row);
            ensureOrderCanBeEdited(row.getOrderId());
            adjustStock(current, row);

            OrderDetail saved = store.details().save(row);
            if (!oldOrderId.equals(saved.getOrderId())) store.recalcOrderTotal(oldOrderId);
            store.recalcOrderTotal(saved.getOrderId());
            return saved;
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.transaction.annotation.Transactional
    public void delete(@PathVariable Long id) {
        synchronized (store) {
            OrderDetail current = one(store.details(), id);
            Long orderId = current.getOrderId();
            CustomerOrder order = store.orders().findById(orderId).orElseThrow();
            if (!"Đã hủy".equals(order.getStatus())) adjustStock(current, null);
            store.details().deleteById(id);
            store.recalcOrderTotal(orderId);
        }
    }

    @Override protected void validate(OrderDetail d) {
        if (d.getOrderId() == null || store.orders().findById(d.getOrderId()).isEmpty()) throw bad("Đơn hàng không tồn tại");
        if (d.getProductId() == null || store.products().findById(d.getProductId()).isEmpty()) throw bad("Sản phẩm không tồn tại");
        d.setOrder(store.orders().findById(d.getOrderId()).orElseThrow());
        d.setProduct(store.products().findById(d.getProductId()).orElseThrow());
        if (d.getQuantity() == null || d.getQuantity() < 1 || d.getQuantity() > 999)
            throw bad("Số lượng phải từ 1 đến 999");
        BigDecimal price = d.getUnitPrice();
        if (price == null) price = store.products().findById(d.getProductId()).orElseThrow().getPrice();
        if (price == null || price.signum() < 0) throw bad("Đơn giá không hợp lệ");
        d.setUnitPrice(price);
        d.setSubtotal(price.multiply(BigDecimal.valueOf(d.getQuantity())));
    }

    private void ensureOrderCanBeEdited(Long orderId) {
        CustomerOrder order = store.orders().findById(orderId).orElseThrow();
        if ("Đã hủy".equals(order.getStatus())) throw bad("Không thể thêm hoặc sửa chi tiết của đơn đã hủy");
    }

    private void adjustStock(OrderDetail current, OrderDetail replacement) {
        Long oldProductId = current == null ? null : current.getProductId();
        int oldQuantity = current == null ? 0 : current.getQuantity();
        Long newProductId = replacement == null ? null : replacement.getProductId();
        int newQuantity = replacement == null ? 0 : replacement.getQuantity();

        Product oldProduct = oldProductId == null ? null : store.products().findById(oldProductId)
                .orElseThrow(() -> bad("Sản phẩm hiện tại không còn tồn tại"));
        Product newProduct = newProductId == null ? null : store.products().findById(newProductId)
                .orElseThrow(() -> bad("Sản phẩm không tồn tại"));

        if (oldProduct != null && oldProductId.equals(newProductId)) {
            int available = oldProduct.getQuantity() + oldQuantity;
            if (newQuantity > available) throw bad("Không đủ tồn kho cho sản phẩm " + oldProduct.getName());
            saveStock(oldProduct, available - newQuantity);
            return;
        }

        if (newProduct != null && newProduct.getQuantity() < newQuantity)
            throw bad("Không đủ tồn kho cho sản phẩm " + newProduct.getName());
        if (oldProduct != null) saveStock(oldProduct, oldProduct.getQuantity() + oldQuantity);
        if (newProduct != null) saveStock(newProduct, newProduct.getQuantity() - newQuantity);
    }

    private void saveStock(Product product, int quantity) {
        product.setQuantity(quantity);
        if (quantity <= 5 && !"Ngừng bán".equals(product.getStatus())) product.setStatus("Sắp hết");
        else if (quantity > 5 && "Sắp hết".equals(product.getStatus())) product.setStatus("Đang bán");
        store.products().save(product);
    }
}

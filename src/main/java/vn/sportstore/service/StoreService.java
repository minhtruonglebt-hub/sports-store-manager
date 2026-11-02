package vn.sportstore.service;

import org.springframework.stereotype.Service;
import vn.sportstore.model.*;
import vn.sportstore.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class StoreService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final OrderDetailRepository details;

    public StoreService(CategoryRepository c, ProductRepository p, CustomerRepository u, OrderRepository o, OrderDetailRepository d) {
        categories = c; products = p; customers = u; orders = o; details = d;
        seed();
    }

    private void seed() {
        Category shoes = category("Giày thể thao", "Giày chạy bộ, bóng đá và tập luyện");
        Category clothes = category("Quần áo", "Trang phục thể thao");
        Category gear = category("Dụng cụ", "Thiết bị và phụ kiện");
        if (products.count() == 0) {
            saveProduct(new Product("Nike Air Zoom Pegasus 40", shoes.getId(), "Nike", "42", "Đen / Trắng", new BigDecimal("3290000"), 12, "Đang bán"), shoes);
            saveProduct(new Product("Áo chạy bộ Dri-FIT", clothes.getId(), "Nike", "L", "Xanh navy", new BigDecimal("890000"), 25, "Đang bán"), clothes);
            saveProduct(new Product("Bóng đá Pro Match", gear.getId(), "SportPro", "5", "Trắng / Đỏ", new BigDecimal("650000"), 8, "Sắp hết"), gear);
        }
        if (customers.count() == 0) {
            customers.save(new Customer("Nguyễn Minh Anh", "0901234567", "minhanh@email.com", "Quận 1, TP. Hồ Chí Minh"));
            customers.save(new Customer("Trần Quốc Bảo", "0912345678", "quocbao@email.com", "Cầu Giấy, Hà Nội"));
        }
        seedDemoOrdersIfEmpty();
    }

    public synchronized void seedDemoOrdersIfEmpty() {
        if (orders.count() > 0) return;
        List<Customer> availableCustomers = customers.findAll();
        List<Product> availableProducts = new ArrayList<>(products.findAll());
        if (availableCustomers.isEmpty() || availableProducts.isEmpty()) return;

        String[] statuses = {"Hoàn thành", "Đang xử lý", "Đã hủy"};
        String[] paymentMethods = {"COD", "BANK_TRANSFER", "MOMO_DEMO"};
        String[] paymentStatuses = {"Đã thu tiền khi giao hàng", "Chờ xác nhận chuyển khoản", "Đã thanh toán (MÔ PHỎNG)"};

        for (int i = 0; i < statuses.length; i++) {
            Product product = availableProducts.stream()
                    .filter(candidate -> candidate.getQuantity() != null && candidate.getQuantity() > 0)
                    .findFirst().orElse(null);
            if (product == null) break;

            Customer customer = availableCustomers.get(i % availableCustomers.size());
            CustomerOrder order = new CustomerOrder();
            order.setCustomer(customer);
            order.setOrderDate(LocalDateTime.now().minusDays(i + 1L).withNano(0));
            order.setStatus(statuses[i]);
            order.setShippingAddress(customer.getAddress());
            order.setPaymentMethod(paymentMethods[i]);
            order.setPaymentStatus(paymentStatuses[i]);
            order.setTotalAmount(product.getPrice());
            orders.save(order);

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(1);
            detail.setUnitPrice(product.getPrice());
            detail.setSubtotal(product.getPrice());
            details.save(detail);

            if (!"Đã hủy".equals(statuses[i])) {
                product.setQuantity(product.getQuantity() - 1);
                if (product.getQuantity() <= 5 && !"Ngừng bán".equals(product.getStatus()))
                    product.setStatus("Sắp hết");
                products.save(product);
            }
            if (product.getQuantity() == 0) availableProducts.remove(product);
        }
    }

    private Category category(String name, String description) {
        return categories.findAll().stream().filter(c -> name.equals(c.getName())).findFirst()
                .orElseGet(() -> categories.save(new Category(name, description)));
    }
    private void saveProduct(Product product, Category category) {
        product.setCategory(category);
        products.save(product);
    }

    public CategoryRepository categories() { return categories; }
    public ProductRepository products() { return products; }
    public CustomerRepository customers() { return customers; }
    public OrderRepository orders() { return orders; }
    public OrderDetailRepository details() { return details; }

    public Map<String, Object> stats() {
        BigDecimal revenue = orders.findAll().stream().filter(o -> !"Đã hủy".equals(o.getStatus()))
                .map(CustomerOrder::getTotalAmount).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        long lowStock = products.findAll().stream().filter(p -> p.getQuantity() != null && p.getQuantity() <= 5).count();
        long pendingOrders = orders.findAll().stream()
                .filter(o -> !"Hoàn thành".equals(o.getStatus()) && !"Đã hủy".equals(o.getStatus())).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories.count()); result.put("products", products.count());
        result.put("customers", customers.count()); result.put("orders", orders.count());
        result.put("orderDetails", details.count()); result.put("revenue", revenue);
        result.put("lowStock", lowStock); result.put("pendingOrders", pendingOrders);
        return result;
    }

    public List<Map<String, Object>> revenueByDay(int days) {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(days - 1L);
        Map<LocalDate, BigDecimal> revenueByDate = new TreeMap<>();
        Map<LocalDate, Long> ordersByDate = new TreeMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = firstDay.plusDays(i);
            revenueByDate.put(date, BigDecimal.ZERO);
            ordersByDate.put(date, 0L);
        }

        for (CustomerOrder order : orders.findAll()) {
            if (order.getOrderDate() == null || "Đã hủy".equals(order.getStatus())) continue;
            LocalDate date = order.getOrderDate().toLocalDate();
            if (!revenueByDate.containsKey(date)) continue;
            revenueByDate.put(date, revenueByDate.get(date)
                    .add(order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount()));
            ordersByDate.put(date, ordersByDate.get(date) + 1);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        revenueByDate.forEach((date, revenue) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("orders", ordersByDate.get(date));
            row.put("revenue", revenue);
            result.add(row);
        });
        return result;
    }

    public boolean hasProductsInCategory(Long categoryId) {
        return products.findAll().stream().anyMatch(p -> categoryId.equals(p.getCategoryId()));
    }
    public boolean hasOrdersOfCustomer(Long customerId) {
        return orders.findAll().stream().anyMatch(o -> customerId.equals(o.getCustomerId()));
    }
    public boolean hasDetailsOfProduct(Long productId) {
        return details.findAll().stream().anyMatch(d -> productId.equals(d.getProductId()));
    }
    public List<OrderDetail> detailsOfOrder(Long orderId) {
        return details.findAll().stream().filter(d -> orderId.equals(d.getOrderId())).toList();
    }
    public BigDecimal totalOfOrder(Long orderId) {
        return detailsOfOrder(orderId).stream().map(OrderDetail::getSubtotal).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public void recalcOrderTotal(Long orderId) {
        orders.findById(orderId).ifPresent(o -> { o.setTotalAmount(totalOfOrder(orderId)); orders.save(o); });
    }
    public void deleteDetailsOfOrder(Long orderId) {
        detailsOfOrder(orderId).forEach(d -> details.deleteById(d.getId()));
    }
    public void restoreStockForOrder(Long orderId) {
        for (OrderDetail detail : detailsOfOrder(orderId)) {
            products.findById(detail.getProductId()).ifPresent(product -> {
                product.setQuantity((product.getQuantity() == null ? 0 : product.getQuantity()) + detail.getQuantity());
                if (product.getQuantity() > 5 && "Sắp hết".equals(product.getStatus())) product.setStatus("Đang bán");
                products.save(product);
            });
        }
    }
}

package vn.sportstore.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.sportstore.model.Customer;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.model.OrderDetail;
import vn.sportstore.model.Product;
import vn.sportstore.repository.CustomerRepository;
import vn.sportstore.repository.OrderDetailRepository;
import vn.sportstore.repository.OrderRepository;
import vn.sportstore.repository.ProductRepository;
import vn.sportstore.repository.UserAccountRepository;
import vn.sportstore.service.StoreService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckoutAndSecurityIntegrationTest {
    private Path uploadedImage;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository products;
    @Autowired private CustomerRepository customers;
    @Autowired private OrderRepository orders;
    @Autowired private OrderDetailRepository orderDetails;
    @Autowired private UserAccountRepository userAccounts;
    @Autowired private StoreService store;

    @Test
    void checkoutCreatesOrderDetailsAndDeductsStock() throws Exception {
        Product product = products.findAll().get(0);
        Customer customer = customers.findAll().get(0);
        int initialStock = product.getQuantity();
        int quantity = 2;
        long initialDetailCount = orderDetails.count();
        BigDecimal expectedTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        String request = objectMapper.writeValueAsString(Map.of(
                "customerId", customer.getId(),
                "paymentMethod", "MOMO_DEMO",
                "items", new Object[]{Map.of("productId", product.getId(), "quantity", quantity)}));

        String response = mockMvc.perform(post("/api/checkout")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertEquals(0, expectedTotal.compareTo(result.path("order").path("totalAmount").decimalValue()));
        assertEquals("MOMO_DEMO", result.path("order").path("paymentMethod").asText());
        assertTrue(result.path("paymentNotice").asText().contains("không có tiền thật"));
        assertEquals(1, result.path("items").size());
        assertEquals(initialStock - quantity, products.findById(product.getId()).orElseThrow().getQuantity());
        assertEquals(initialDetailCount + 1, orderDetails.count());
    }

    @Test
    void checkoutRejectsInsufficientStockWithoutCreatingAnOrder() throws Exception {
        Product product = products.findAll().get(0);
        Customer customer = customers.findAll().get(0);
        int initialStock = product.getQuantity();
        long initialOrderCount = orders.count();
        long initialDetailCount = orderDetails.count();
        String request = objectMapper.writeValueAsString(Map.of(
                "customerId", customer.getId(),
                "items", new Object[]{Map.of("productId", product.getId(), "quantity", initialStock + 1)}));

        mockMvc.perform(post("/api/checkout")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        assertEquals(initialStock, products.findById(product.getId()).orElseThrow().getQuantity());
        assertEquals(initialOrderCount, orders.count());
        assertEquals(initialDetailCount, orderDetails.count());
    }

    @Test
    void demoOrdersAreSeededOnceAndKeepInventoryConsistent() {
        long initialOrders = orders.count();
        long initialDetails = orderDetails.count();
        Map<Long, Integer> initialStock = products.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, Product::getQuantity));
        BigDecimal initialRevenue = orders.findAll().stream()
                .filter(order -> !"Đã hủy".equals(order.getStatus()))
                .map(CustomerOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(initialOrders >= 3);
        assertTrue(initialDetails >= 3);
        assertTrue(initialRevenue.signum() > 0);

        store.seedDemoOrdersIfEmpty();

        assertEquals(initialOrders, orders.count());
        assertEquals(initialDetails, orderDetails.count());
        products.findAll().forEach(product ->
                assertEquals(initialStock.get(product.getId()), product.getQuantity()));
    }

    @Test
    void revenueReportExcludesCancelledOrdersAndExportsUtf8Csv() throws Exception {
        String report = mockMvc.perform(get("/api/dashboard/revenue?days=30")
                        .with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(report).path("items");
        assertEquals(30, items.size());
        BigDecimal reportedRevenue = BigDecimal.ZERO;
        long reportedOrders = 0;
        for (JsonNode item : items) {
            reportedRevenue = reportedRevenue.add(item.path("revenue").decimalValue());
            reportedOrders += item.path("orders").asLong();
        }
        BigDecimal expectedRevenue = orders.findAll().stream()
                .filter(order -> !"Đã hủy".equals(order.getStatus()))
                .map(CustomerOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long expectedOrders = orders.findAll().stream()
                .filter(order -> !"Đã hủy".equals(order.getStatus())).count();
        assertEquals(0, expectedRevenue.compareTo(reportedRevenue));
        assertEquals(expectedOrders, reportedOrders);

        var csvResponse = mockMvc.perform(get("/api/dashboard/revenue.csv?days=7")
                        .with(user("staff").roles("STAFF")))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String csv = csvResponse.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFNgày,Số đơn,Doanh thu (VND)"));
        assertTrue(csvResponse.getHeader("Content-Disposition").contains("sportiva-revenue-7-days.csv"));
        assertEquals(8, csv.lines().count());

        mockMvc.perform(get("/api/dashboard/revenue?days=14")
                        .with(user("staff").roles("STAFF")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productImageUploadValidatesContentAndServesStoredImage() throws Exception {
        Product product = products.findAll().get(0);
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lS8AAAAASUVORK5CYII=");
        MockMultipartFile validImage = new MockMultipartFile("image", "pixel.png", "image/png", png);

        String uploadResponse = mockMvc.perform(multipart("/api/products/{id}/image", product.getId())
                        .file(validImage)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String imageUrl = objectMapper.readTree(uploadResponse).path("imageUrl").asText();
        assertTrue(imageUrl.matches("/uploads/products/[0-9a-f-]{36}\\.png"));
        assertEquals(imageUrl, products.findById(product.getId()).orElseThrow().getImageUrl());

        String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        uploadedImage = Path.of(System.getProperty("java.io.tmpdir"), "sportiva-test-uploads", filename);
        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(png));

        MockMultipartFile fakeImage = new MockMultipartFile("image", "fake.png", "image/png", "not a PNG".getBytes());
        mockMvc.perform(multipart("/api/products/{id}/image", product.getId())
                        .file(fakeImage)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(imageUrl, products.findById(product.getId()).orElseThrow().getImageUrl());
    }

    @Test
    void adminCanCreatePersistentUsersAndCannotRemoveTheLastAdmin() throws Exception {
        String createRequest = """
                {"username":"newstaff","password":"SecurePassword!123","role":"STAFF"}
                """;
        String created = mockMvc.perform(post("/api/users")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode account = objectMapper.readTree(created);
        assertEquals("newstaff", account.path("username").asText());
        assertEquals("STAFF", account.path("role").asText());
        assertTrue(!account.has("passwordHash"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newstaff\",\"password\":\"SecurePassword!123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STAFF\",\"enabled\":false}"))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/users/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void bootstrapUsersAreStoredWithEncodedPasswords() {
        var admin = userAccounts.findByUsernameIgnoreCase("admin").orElseThrow();
        var staff = userAccounts.findByUsernameIgnoreCase("staff").orElseThrow();
        assertTrue(admin.getPasswordHash().startsWith("$2"));
        assertTrue(staff.getPasswordHash().startsWith("$2"));
        assertTrue(!admin.getPasswordHash().equals("Admin@123"));
        assertTrue(!staff.getPasswordHash().equals("Staff@123"));
    }

    @Test
    void healthEndpointReportsDatabaseStatusWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("UP"));
    }

    @Test
    void staffCannotReadOrCreateUserAccounts() throws Exception {
        mockMvc.perform(get("/api/users").with(user("staff").roles("STAFF")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users")
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked\",\"password\":\"SecurePassword!123\",\"role\":\"STAFF\"}"))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.AfterEach
    void removeUploadedTestImage() throws java.io.IOException {
        if (uploadedImage != null) Files.deleteIfExists(uploadedImage);
    }

    @Test
    void apiRequiresLoginAndStaffCannotUseAdminOnlyMutations() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/categories")
                        .with(user("staff").roles("STAFF"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Danh mục kiểm thử\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/categories")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Danh mục thiếu CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editingAndDeletingOrderDetailsKeepsStockAndOrderTotalConsistent() throws Exception {
        Product product = products.findAll().get(0);
        Customer customer = customers.findAll().get(0);
        int initialStock = product.getQuantity();
        CustomerOrder order = createOrder(customer);
        String createRequest = objectMapper.writeValueAsString(Map.of(
                "orderId", order.getId(),
                "productId", product.getId(),
                "quantity", 2,
                "unitPrice", product.getPrice()));

        String createdResponse = mockMvc.perform(post("/api/order-details")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdResponse);
        Long detailId = created.path("id").asLong();
        BigDecimal unitPrice = product.getPrice();

        assertEquals(initialStock - 2, products.findById(product.getId()).orElseThrow().getQuantity());
        assertEquals(0, unitPrice.multiply(BigDecimal.valueOf(2))
                .compareTo(orders.findById(order.getId()).orElseThrow().getTotalAmount()));

        String excessiveUpdate = objectMapper.writeValueAsString(Map.of(
                "orderId", order.getId(),
                "productId", product.getId(),
                "quantity", initialStock + 1,
                "unitPrice", unitPrice));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/order-details/{id}", detailId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(excessiveUpdate))
                .andExpect(status().isBadRequest());
        assertEquals(initialStock - 2, products.findById(product.getId()).orElseThrow().getQuantity());
        assertEquals(0, unitPrice.multiply(BigDecimal.valueOf(2))
                .compareTo(orders.findById(order.getId()).orElseThrow().getTotalAmount()));

        String updateRequest = objectMapper.writeValueAsString(Map.of(
                "orderId", order.getId(),
                "productId", product.getId(),
                "quantity", 4,
                "unitPrice", unitPrice));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/order-details/{id}", detailId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());

        assertEquals(initialStock - 4, products.findById(product.getId()).orElseThrow().getQuantity());
        assertEquals(0, unitPrice.multiply(BigDecimal.valueOf(4))
                .compareTo(orders.findById(order.getId()).orElseThrow().getTotalAmount()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/order-details/{id}", detailId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertEquals(initialStock, products.findById(product.getId()).orElseThrow().getQuantity());
        assertEquals(BigDecimal.ZERO, orders.findById(order.getId()).orElseThrow().getTotalAmount());
    }

    @Test
    void cancellingOrderRestoresStockExactlyOnce() throws Exception {
        Product product = products.findAll().get(0);
        Customer customer = customers.findAll().get(0);
        int initialStock = product.getQuantity();
        CustomerOrder order = createOrder(customer);

        product.setQuantity(initialStock - 2);
        products.save(product);
        OrderDetail detail = new OrderDetail();
        detail.setOrder(order);
        detail.setProduct(product);
        detail.setQuantity(2);
        detail.setUnitPrice(product.getPrice());
        detail.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(2)));
        orderDetails.save(detail);

        String updateRequest = objectMapper.writeValueAsString(Map.of(
                "customerId", customer.getId(),
                "orderDate", order.getOrderDate().toString(),
                "status", "Đã hủy",
                "totalAmount", detail.getSubtotal()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/orders/{id}", order.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());

        assertEquals(initialStock, products.findById(product.getId()).orElseThrow().getQuantity());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/orders/{id}", order.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());
        assertEquals(initialStock, products.findById(product.getId()).orElseThrow().getQuantity());
    }

    private CustomerOrder createOrder(Customer customer) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        order.setOrderDate(LocalDateTime.now().withNano(0));
        order.setStatus("Chờ xác nhận");
        order.setTotalAmount(BigDecimal.ZERO);
        return orders.save(order);
    }
}

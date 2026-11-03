package vn.sportstore.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.sportstore.repository.CustomerRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerStoreIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRepository customers;

    @Test
    void customerCanRegisterShopAndOnlySeeTheirOwnOrders() throws Exception {
        mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/customer/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"shop-customer",
                                  "password":"Customer@12345",
                                  "fullName":"Khách mua hàng",
                                  "email":"shop-customer@example.com",
                                  "phone":"0901234567"
                                }
                                """))
                .andExpect(status().isOk());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"shop-customer","password":"Customer@12345"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertTrue(session != null);

        JsonNode profile = objectMapper.readTree(mockMvc.perform(get("/api/store/me").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("Khách mua hàng", profile.path("fullName").asText());

        long anotherCustomerId = customers.findAll().get(0).getId();
        JsonNode productList = objectMapper.readTree(mockMvc.perform(get("/api/store/products"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long productId = productList.get(0).path("id").asLong();

        mockMvc.perform(post("/api/checkout/customer")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "customerId":%d,
                                  "shippingAddress":"12 Đường Thể Thao, TP. Hồ Chí Minh",
                                  "customerPhone":"0901234567",
                                  "customerNote":"Giao giờ hành chính",
                                  "paymentMethod":"COD",
                                  "items":[{"productId":%d,"quantity":1}]
                                }
                                """.formatted(anotherCustomerId, productId)))
                .andExpect(status().isOk());

        JsonNode orders = objectMapper.readTree(mockMvc.perform(get("/api/store/orders").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, orders.size());
        assertTrue(orders.get(0).path("id").asLong() > 0);

        JsonNode adminOrders = objectMapper.readTree(mockMvc.perform(get("/api/orders/online")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, adminOrders.size());
        assertEquals("Khách mua hàng", adminOrders.get(0).path("customerName").asText());
        assertEquals("shop-customer@example.com", adminOrders.get(0).path("customerEmail").asText());
        assertEquals("0901234567", adminOrders.get(0).path("customerPhone").asText());
        assertEquals("Giao giờ hành chính", adminOrders.get(0).path("customerNote").asText());
        assertEquals(1, adminOrders.get(0).path("items").size());
        long customerOrderId = adminOrders.get(0).path("id").asLong();

        mockMvc.perform(post("/api/checkout/customer")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "shippingAddress":"12 Đường Thể Thao, TP. Hồ Chí Minh",
                                  "customerPhone":"0901234567",
                                  "paymentMethod":"CARD_DEMO",
                                  "items":[{"productId":%d,"quantity":1}]
                                }
                                """.formatted(productId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/checkout/customer")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "shippingAddress":"12 Đường Thể Thao, TP. Hồ Chí Minh",
                                  "customerPhone":"0912345678",
                                  "customerNote":"Gọi trước khi giao",
                                  "paymentMethod":"BANK_TRANSFER",
                                  "items":[{"productId":%d,"quantity":1}]
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        JsonNode allOnlineOrders = objectMapper.readTree(mockMvc.perform(get("/api/orders/online")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode pendingBankOrder = java.util.stream.StreamSupport.stream(
                        allOnlineOrders.spliterator(), false)
                .filter(order -> "BANK_TRANSFER".equals(order.path("paymentMethod").asText()))
                .findFirst().orElseThrow();
        assertEquals("Chờ xác nhận chuyển khoản", pendingBankOrder.path("paymentStatus").asText());
        long bankOrderId = pendingBankOrder.path("id").asLong();
        mockMvc.perform(post("/api/orders/{id}/payment-confirmation", bankOrderId)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf()))
                .andExpect(status().isOk());

        JsonNode customerOrdersAfterConfirmation = objectMapper.readTree(
                mockMvc.perform(get("/api/store/orders").session(session))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertTrue(java.util.stream.StreamSupport.stream(
                        customerOrdersAfterConfirmation.spliterator(), false)
                .anyMatch(order -> order.path("id").asLong() == bankOrderId
                        && "Đã xác nhận chuyển khoản".equals(order.path("paymentStatus").asText())));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/orders/{id}/status", customerOrderId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"status":"Đang xử lý"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/payment-confirmation", customerOrderId)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/orders/{id}/status", customerOrderId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"status":"Hoàn thành"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/payment-confirmation", customerOrderId)
                        .with(user("staff").roles("STAFF"))
                        .with(csrf()))
                .andExpect(status().isOk());

        JsonNode updatedCustomerOrders = objectMapper.readTree(
                mockMvc.perform(get("/api/store/orders").session(session))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertTrue(java.util.stream.StreamSupport.stream(updatedCustomerOrders.spliterator(), false)
                .anyMatch(order -> order.path("id").asLong() == customerOrderId
                        && "Hoàn thành".equals(order.path("status").asText())
                        && "Đã thu tiền khi giao hàng".equals(order.path("paymentStatus").asText())));

        mockMvc.perform(get("/api/orders").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerRegistrationRejectsShortPasswords() throws Exception {
        mockMvc.perform(post("/api/auth/customer/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username":"short-password-customer",
                                  "password":"short",
                                  "fullName":"Khách mua hàng",
                                  "email":"short@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}

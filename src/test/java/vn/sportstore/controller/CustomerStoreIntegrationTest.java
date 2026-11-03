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
        assertEquals(1, adminOrders.get(0).path("items").size());
        long customerOrderId = adminOrders.get(0).path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/orders/{id}/status", customerOrderId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"status":"Đang xử lý"}
                                """))
                .andExpect(status().isOk());

        JsonNode updatedCustomerOrders = objectMapper.readTree(
                mockMvc.perform(get("/api/store/orders").session(session))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertEquals("Đang xử lý", updatedCustomerOrders.get(0).path("status").asText());

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

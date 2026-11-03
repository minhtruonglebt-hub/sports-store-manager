package vn.sportstore.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.sportstore.model.Category;
import vn.sportstore.model.Customer;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.model.OrderDetail;
import vn.sportstore.model.Product;
import vn.sportstore.repository.CategoryRepository;
import vn.sportstore.repository.CustomerRepository;
import vn.sportstore.repository.OrderDetailRepository;
import vn.sportstore.repository.OrderRepository;
import vn.sportstore.repository.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductPaginationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CustomerRepository customers;
    @Autowired private OrderRepository orders;
    @Autowired private OrderDetailRepository orderDetails;

    @Test
    void productFilteringUsesStableDatabasePagesAndReportsTotals() throws Exception {
        JsonNode firstPage = objectMapper.readTree(mockMvc.perform(get("/api/products/filter")
                        .param("q", "nike")
                        .param("page", "0")
                        .param("size", "1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode secondPage = objectMapper.readTree(mockMvc.perform(get("/api/products/filter")
                        .param("q", "nike")
                        .param("page", "1")
                        .param("size", "1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(1, firstPage.path("items").size());
        assertEquals(1, secondPage.path("items").size());
        assertEquals(2, firstPage.path("total").asInt());
        assertEquals(2, firstPage.path("totalPages").asInt());
        assertNotEquals(firstPage.path("items").get(0).path("id").asLong(),
                secondPage.path("items").get(0).path("id").asLong());
    }

    @Test
    void productSearchRejectsInvalidPageBounds() throws Exception {
        mockMvc.perform(get("/api/products/filter")
                        .param("page", "0")
                        .param("size", "101")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productPaginationHandlesMoreThanOneHundredRows() throws Exception {
        Category category = categories.findAll().get(0);
        for (int i = 0; i < 120; i++) {
            Product product = new Product("Load test item " + i, category.getId(), "Sportiva",
                    "M", "Black", new BigDecimal("10000"), 20, "Đang bán");
            product.setCategory(category);
            products.save(product);
        }

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/products/filter")
                        .param("page", "5")
                        .param("size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(20, page.path("items").size());
        assertEquals(123, page.path("total").asInt());
        assertEquals(7, page.path("totalPages").asInt());
    }

    @Test
    void customerOrderAndDetailListsArePagedInTheDatabase() throws Exception {
        Category category = categories.findAll().get(0);
        for (int i = 0; i < 105; i++) {
            Customer customer = customers.save(new Customer("Load test customer " + i,
                    "0901234567", "loadtest" + i + "@example.test", "Test address"));
            Product product = new Product("Load test product " + i, category.getId(), "Sportiva",
                    "M", "Black", new BigDecimal("10000"), 20, "Đang bán");
            product.setCategory(category);
            product = products.save(product);

            CustomerOrder order = new CustomerOrder();
            order.setCustomer(customer);
            order.setOrderDate(LocalDateTime.now().minusMinutes(i));
            order.setStatus("Load test order");
            order.setTotalAmount(new BigDecimal("10000"));
            order = orders.save(order);

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(1);
            detail.setUnitPrice(new BigDecimal("10000"));
            detail.setSubtotal(new BigDecimal("10000"));
            orderDetails.save(detail);
        }

        assertPage("/api/customers/page", "load test customer", 105);
        assertPage("/api/orders/page", "load test order", 105);
        assertPage("/api/order-details/page", "load test product", 105);
    }

    private void assertPage(String endpoint, String query, int expectedTotal) throws Exception {
        JsonNode page = objectMapper.readTree(mockMvc.perform(get(endpoint)
                        .param("q", query)
                        .param("page", "2")
                        .param("size", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(20, page.path("items").size(), endpoint);
        assertEquals(expectedTotal, page.path("total").asInt(), endpoint);
        assertEquals(6, page.path("totalPages").asInt(), endpoint);
    }
}

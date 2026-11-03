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
import vn.sportstore.model.Product;
import vn.sportstore.repository.CategoryRepository;
import vn.sportstore.repository.ProductRepository;

import java.math.BigDecimal;

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
}

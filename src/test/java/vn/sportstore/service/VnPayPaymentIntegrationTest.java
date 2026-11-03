package vn.sportstore.service;

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
import vn.sportstore.model.Product;
import vn.sportstore.repository.ProductRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "sportstore.payment.vnpay.tmn-code=SPORT123",
        "sportstore.payment.vnpay.hash-secret=test-vnpay-hash-secret",
        "sportstore.payment.vnpay.return-url=https://shop.example.test/api/payments/vnpay/return"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VnPayPaymentIntegrationTest {
    private static final String HASH_SECRET = "test-vnpay-hash-secret";
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository products;

    @Test
    void customerCheckoutCreatesSignedVnPayUrlAndOnlySignedCallbackConfirmsPayment() throws Exception {
        mockMvc.perform(post("/api/auth/customer/register")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"vnpay-customer","password":"Customer@12345",
                                 "fullName":"Khách VNPay","email":"vnpay@example.com"}
                                """))
                .andExpect(status().isOk());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"vnpay-customer","password":"Customer@12345"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        assertTrue(session != null);

        Product product = products.findAll().stream()
                .filter(candidate -> candidate.getQuantity() > 0).findFirst().orElseThrow();
        JsonNode checkout = objectMapper.readTree(mockMvc.perform(post("/api/checkout/customer")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"shippingAddress":"1 Đường Thể Thao","customerPhone":"0901234567",
                                 "paymentMethod":"VNPAY","items":[{"productId":%d,"quantity":1}]}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String paymentUrl = checkout.path("paymentUrl").asText();
        assertTrue(paymentUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));

        Map<String, String> paymentParameters = parseQuery(paymentUrl.substring(paymentUrl.indexOf('?') + 1));
        String paymentSignature = paymentParameters.remove("vnp_SecureHash");
        assertEquals(VnPayService.sign(new TreeMap<>(paymentParameters), HASH_SECRET), paymentSignature);

        String orderId = checkout.path("order").path("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/orders/{id}/status", orderId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"status":"Đã hủy"}
                                """))
                .andExpect(status().isBadRequest());
        String amount = checkout.path("order").path("totalAmount").decimalValue()
                .movePointRight(2).toBigIntegerExact().toString();
        Map<String, String> callback = new LinkedHashMap<>();
        callback.put("vnp_TmnCode", "SPORT123");
        callback.put("vnp_TxnRef", orderId);
        callback.put("vnp_Amount", amount);
        callback.put("vnp_ResponseCode", "00");
        callback.put("vnp_TransactionStatus", "00");
        callback.put("vnp_TransactionNo", "123456789");
        callback.put("vnp_BankCode", "NCB");
        callback.put("vnp_PayDate", "20261103220000");

        Map<String, String> tampered = new LinkedHashMap<>(callback);
        tampered.put("vnp_Amount", Long.toString(Long.parseLong(amount) + 100));
        tampered.put("vnp_SecureHash", VnPayService.sign(new TreeMap<>(callback), HASH_SECRET));
        mockMvc.perform(get("/api/payments/vnpay/ipn").params(toParams(tampered)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.RspCode").value("97"));

        callback.put("vnp_SecureHash", VnPayService.sign(new TreeMap<>(callback), HASH_SECRET));
        mockMvc.perform(get("/api/payments/vnpay/ipn").params(toParams(callback)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.RspCode").value("00"));
        mockMvc.perform(get("/api/payments/vnpay/ipn").params(toParams(callback)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.RspCode").value("02"));

        JsonNode customerOrders = objectMapper.readTree(mockMvc.perform(get("/api/store/orders").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(java.util.stream.StreamSupport.stream(customerOrders.spliterator(), false)
                .anyMatch(order -> order.path("id").asText().equals(orderId)
                        && "Đã thanh toán VNPay".equals(order.path("paymentStatus").asText())
                        && "Đang xử lý".equals(order.path("status").asText())));

        int stockAfterPaidOrder = products.findById(product.getId()).orElseThrow().getQuantity();
        JsonNode failedCheckout = objectMapper.readTree(mockMvc.perform(post("/api/checkout/customer")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"shippingAddress":"1 Đường Thể Thao","customerPhone":"0901234567",
                                 "paymentMethod":"VNPAY","items":[{"productId":%d,"quantity":1}]}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String failedOrderId = failedCheckout.path("order").path("id").asText();
        String failedAmount = failedCheckout.path("order").path("totalAmount").decimalValue()
                .movePointRight(2).toBigIntegerExact().toString();
        Map<String, String> failedCallback = new LinkedHashMap<>();
        failedCallback.put("vnp_TmnCode", "SPORT123");
        failedCallback.put("vnp_TxnRef", failedOrderId);
        failedCallback.put("vnp_Amount", failedAmount);
        failedCallback.put("vnp_ResponseCode", "24");
        failedCallback.put("vnp_TransactionStatus", "02");
        failedCallback.put("vnp_SecureHash", VnPayService.sign(new TreeMap<>(failedCallback), HASH_SECRET));
        mockMvc.perform(get("/api/payments/vnpay/ipn").params(toParams(failedCallback)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.RspCode").value("00"));
        assertEquals(stockAfterPaidOrder, products.findById(product.getId()).orElseThrow().getQuantity());

        JsonNode ordersAfterDecline = objectMapper.readTree(
                mockMvc.perform(get("/api/store/orders").session(session))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertTrue(java.util.stream.StreamSupport.stream(ordersAfterDecline.spliterator(), false)
                .anyMatch(order -> order.path("id").asText().equals(failedOrderId)
                        && "Đã hủy".equals(order.path("status").asText())
                        && "Thanh toán VNPay thất bại".equals(order.path("paymentStatus").asText())));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }

    private static org.springframework.util.MultiValueMap<String, String> toParams(Map<String, String> values) {
        org.springframework.util.LinkedMultiValueMap<String, String> params =
                new org.springframework.util.LinkedMultiValueMap<>();
        values.forEach(params::add);
        return params;
    }
}

package vn.sportstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.sportstore.model.CustomerOrder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

@Service
public class VnPayService {
    private static final DateTimeFormatter VNPAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final String terminalCode;
    private final String hashSecret;
    private final String paymentUrl;
    private final String returnUrl;

    public VnPayService(
            @Value("${sportstore.payment.vnpay.tmn-code:}") String terminalCode,
            @Value("${sportstore.payment.vnpay.hash-secret:}") String hashSecret,
            @Value("${sportstore.payment.vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String paymentUrl,
            @Value("${sportstore.payment.vnpay.return-url:http://localhost:8081/api/payments/vnpay/return}") String returnUrl) {
        this.terminalCode = terminalCode;
        this.hashSecret = hashSecret;
        this.paymentUrl = paymentUrl;
        this.returnUrl = returnUrl;
    }

    public boolean isConfigured() {
        return !terminalCode.isBlank() && !hashSecret.isBlank()
                && paymentUrl.startsWith("https://") && returnUrl.startsWith("https://");
    }

    public String createPaymentUrl(CustomerOrder order, String clientIp) {
        if (!isConfigured()) throw new IllegalStateException("VNPay chưa được cửa hàng cấu hình");
        long amount = order.getTotalAmount().movePointRight(2).longValueExact();
        LocalDateTime now = LocalDateTime.now(VIETNAM_ZONE);
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "pay");
        parameters.put("vnp_TmnCode", terminalCode);
        parameters.put("vnp_Amount", Long.toString(amount));
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_TxnRef", order.getId().toString());
        parameters.put("vnp_OrderInfo", "Thanh toan don hang " + order.getId());
        parameters.put("vnp_OrderType", "other");
        parameters.put("vnp_Locale", "vn");
        parameters.put("vnp_ReturnUrl", returnUrl);
        parameters.put("vnp_IpAddr", normalizeIp(clientIp));
        parameters.put("vnp_CreateDate", VNPAY_DATE.format(now));
        parameters.put("vnp_ExpireDate", VNPAY_DATE.format(now.plusMinutes(15)));

        String query = canonicalQuery(parameters);
        String signature = hmacSha512(hashSecret, query);
        return paymentUrl + (paymentUrl.contains("?") ? "&" : "?")
                + query + "&vnp_SecureHash=" + signature;
    }

    public boolean hasValidSignature(Map<String, String> parameters) {
        String received = parameters.get("vnp_SecureHash");
        if (received == null || !received.matches("(?i)[0-9a-f]{128}")
                || !terminalCode.equals(parameters.get("vnp_TmnCode")) || hashSecret.isBlank()) return false;
        Map<String, String> signed = new TreeMap<>();
        parameters.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash")
                    && !key.equals("vnp_SecureHashType")) signed.put(key, value);
        });
        byte[] expected = hmacSha512(hashSecret, canonicalQuery(signed)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = received.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    static String sign(Map<String, String> parameters, String secret) {
        return hmacSha512(secret, canonicalQuery(new TreeMap<>(parameters)));
    }

    private static String canonicalQuery(Map<String, String> parameters) {
        StringBuilder query = new StringBuilder();
        parameters.forEach((key, value) -> {
            if (!query.isEmpty()) query.append('&');
            query.append(encode(key)).append('=').append(encode(value));
        });
        return query.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 encoding is unavailable", exception);
        }
    }

    private static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể ký dữ liệu thanh toán VNPay", exception);
        }
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return "127.0.0.1";
        if (ip.equals("0:0:0:0:0:0:0:1")) return "127.0.0.1";
        return ip.length() <= 45 ? ip : ip.substring(0, 45);
    }
}

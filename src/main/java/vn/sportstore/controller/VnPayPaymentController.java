package vn.sportstore.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import vn.sportstore.model.CustomerOrder;
import vn.sportstore.repository.OrderRepository;
import vn.sportstore.service.StoreService;
import vn.sportstore.service.VnPayService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/vnpay")
public class VnPayPaymentController {
    private final VnPayService vnPay;
    private final OrderRepository orders;
    private final StoreService store;

    public VnPayPaymentController(VnPayService vnPay, OrderRepository orders, StoreService store) {
        this.vnPay = vnPay;
        this.orders = orders;
        this.store = store;
    }

    @GetMapping("/return")
    public RedirectView paymentReturn(HttpServletRequest request) {
        Map<String, String> parameters = parameters(request);
        if (!vnPay.hasValidSignature(parameters))
            return new RedirectView("/shop.html?payment=failed", true, false);
        try {
            long orderId = Long.parseLong(parameters.getOrDefault("vnp_TxnRef", ""));
            CustomerOrder order = orders.findById(orderId).orElse(null);
            if (order == null || !"ONLINE".equals(order.getOrderSource())
                    || !"VNPAY".equals(order.getPaymentMethod()))
                return new RedirectView("/shop.html?payment=failed", true, false);
            if ("Đã thanh toán VNPay".equals(order.getPaymentStatus()))
                return new RedirectView("/shop.html?payment=success", true, false);
            if ("Đã hủy".equals(order.getStatus())
                    || "Thanh toán VNPay thất bại".equals(order.getPaymentStatus()))
                return new RedirectView("/shop.html?payment=failed", true, false);
            return new RedirectView("/shop.html?payment=processing", true, false);
        } catch (NumberFormatException exception) {
            return new RedirectView("/shop.html?payment=failed", true, false);
        }
    }

    @GetMapping("/ipn")
    @Transactional
    public ResponseEntity<Map<String, String>> ipn(HttpServletRequest request) {
        CallbackResult result = processCallbackResult(parameters(request));
        return ResponseEntity.ok(Map.of("RspCode", result.code(), "Message", result.message()));
    }

    private boolean processCallback(Map<String, String> parameters) {
        return processCallbackResult(parameters).success();
    }

    private CallbackResult processCallbackResult(Map<String, String> parameters) {
        if (!vnPay.hasValidSignature(parameters)) return new CallbackResult(false, "97", "Invalid signature");
        Long orderId;
        try {
            orderId = Long.valueOf(parameters.getOrDefault("vnp_TxnRef", ""));
        } catch (NumberFormatException exception) {
            return new CallbackResult(false, "01", "Order not found");
        }

        synchronized (store) {
            CustomerOrder order = orders.findById(orderId).orElse(null);
            if (order == null || !"ONLINE".equals(order.getOrderSource())
                    || !"VNPAY".equals(order.getPaymentMethod()))
                return new CallbackResult(false, "01", "Order not found");
            long expectedAmount;
            try {
                expectedAmount = order.getTotalAmount().movePointRight(2).longValueExact();
            } catch (ArithmeticException exception) {
                return new CallbackResult(false, "04", "Invalid amount");
            }
            if (!Long.toString(expectedAmount).equals(parameters.get("vnp_Amount")))
                return new CallbackResult(false, "04", "Invalid amount");
            if ("Đã thanh toán VNPay".equals(order.getPaymentStatus()))
                return new CallbackResult(true, "02", "Order already confirmed");
            if ("Đã hủy".equals(order.getStatus()))
                return new CallbackResult(false, "01", "Order is cancelled");

            if ("00".equals(parameters.get("vnp_ResponseCode"))
                    && "00".equals(parameters.get("vnp_TransactionStatus"))) {
                order.setPaymentStatus("Đã thanh toán VNPay");
                if ("Chờ xác nhận".equals(order.getStatus())) order.setStatus("Đang xử lý");
                orders.save(order);
                return new CallbackResult(true, "00", "Confirm Success");
            }
            order.setStatus("Đã hủy");
            order.setPaymentStatus("Thanh toán VNPay thất bại");
            orders.save(order);
            store.restoreStockForOrder(orderId);
            return new CallbackResult(false, "00", "Confirm Success");
        }
    }

    private static Map<String, String> parameters(HttpServletRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length == 1) result.put(key, values[0]);
            else if (values != null) result.put(key, "");
        });
        return result;
    }

    private record CallbackResult(boolean success, String code, String message) {}
}

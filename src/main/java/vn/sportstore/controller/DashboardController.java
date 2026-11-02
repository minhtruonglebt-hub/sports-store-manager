package vn.sportstore.controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.service.StoreService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/dashboard")
public class DashboardController {
 private final StoreService s;
 public DashboardController(StoreService s){this.s=s;}
 @GetMapping public Map<String,Object> stats(){return s.stats();}

 @GetMapping("/revenue")
 public Map<String,Object> revenue(@RequestParam(defaultValue="30") int days) {
  validateDays(days);
  return Map.of("days", days, "items", s.revenueByDay(days));
 }

 @GetMapping(value="/revenue.csv", produces="text/csv")
 public ResponseEntity<byte[]> revenueCsv(@RequestParam(defaultValue="30") int days) {
  validateDays(days);
  StringBuilder csv = new StringBuilder("\uFEFFNgày,Số đơn,Doanh thu (VND)\r\n");
  List<Map<String,Object>> rows = s.revenueByDay(days);
  for (Map<String,Object> row : rows) {
   csv.append(row.get("date")).append(',')
      .append(row.get("orders")).append(',')
      .append(row.get("revenue")).append("\r\n");
  }
  return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sportiva-revenue-" + days + "-days.csv\"")
          .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
          .body(csv.toString().getBytes(StandardCharsets.UTF_8));
 }

 private static void validateDays(int days) {
  if (days != 7 && days != 30 && days != 90)
   throw new org.springframework.web.server.ResponseStatusException(
           org.springframework.http.HttpStatus.BAD_REQUEST, "Khoảng báo cáo phải là 7, 30 hoặc 90 ngày");
 }
}

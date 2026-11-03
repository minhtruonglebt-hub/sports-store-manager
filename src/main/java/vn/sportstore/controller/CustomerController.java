package vn.sportstore.controller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.model.Customer;
import vn.sportstore.service.StoreService;
import java.util.List;
@RestController @RequestMapping("/api/customers")
public class CustomerController extends CrudControllerSupport<Customer> {
 private final StoreService store; public CustomerController(StoreService store){this.store=store;}
 @GetMapping public List<Customer> all(){return all(store.customers());}
 @GetMapping("/page") public java.util.Map<String,Object> page(
         @RequestParam(defaultValue="") String q,
         @RequestParam(defaultValue="0") int page,
         @RequestParam(defaultValue="10") int size) {
  validatePage(q,page,size);
  Page<Customer> result=store.customers().search(q.trim().toLowerCase(java.util.Locale.ROOT),
          numericId(q),PageRequest.of(page,size,Sort.by("id").ascending()));
  return java.util.Map.of("items",result.getContent(),"page",result.getNumber(),"size",result.getSize(),
          "total",result.getTotalElements(),"totalPages",result.getTotalPages());
 }
 @GetMapping("/{id}") public Customer one(@PathVariable Long id){return one(store.customers(),id);}
 @PostMapping public Customer create(@RequestBody Customer x){return create(store.customers(),x);}
 @PutMapping("/{id}") public Customer update(@PathVariable Long id,@RequestBody Customer x){return update(store.customers(),id,x);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){remove(store.customers(),id);}
 @Override protected void validate(Customer c){if(blank(c.getFullName()))throw bad("Tên khách hàng không được để trống");c.setFullName(c.getFullName().trim());if(c.getEmail()!=null&&!c.getEmail().isBlank()&&!c.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw bad("Email không đúng định dạng");if(c.getPhone()!=null&&!c.getPhone().isBlank()&&!c.getPhone().matches("^[0-9+() .-]{8,20}$"))throw bad("Số điện thoại không đúng định dạng");}
 @Override protected void beforeDelete(Customer c){if(store.hasOrdersOfCustomer(c.getId()))throw conflict("Khách hàng đã có đơn hàng, không thể xóa");}
 private static void validatePage(String q,int page,int size){if(q.length()>100||page<0||page>10000||size<1||size>100)throw bad("Từ khóa, trang hoặc số dòng mỗi trang không hợp lệ");}
 private static Long numericId(String q){try{return Long.valueOf(q.trim());}catch(NumberFormatException ignored){return null;}}
}

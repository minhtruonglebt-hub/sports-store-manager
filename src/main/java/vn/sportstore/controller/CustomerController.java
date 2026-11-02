package vn.sportstore.controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.model.Customer;
import vn.sportstore.service.StoreService;
import java.util.List;
@RestController @RequestMapping("/api/customers")
public class CustomerController extends CrudControllerSupport<Customer> {
 private final StoreService store; public CustomerController(StoreService store){this.store=store;}
 @GetMapping public List<Customer> all(){return all(store.customers());}
 @GetMapping("/{id}") public Customer one(@PathVariable Long id){return one(store.customers(),id);}
 @PostMapping public Customer create(@RequestBody Customer x){return create(store.customers(),x);}
 @PutMapping("/{id}") public Customer update(@PathVariable Long id,@RequestBody Customer x){return update(store.customers(),id,x);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){remove(store.customers(),id);}
 @Override protected void validate(Customer c){if(blank(c.getFullName()))throw bad("Tên khách hàng không được để trống");c.setFullName(c.getFullName().trim());if(c.getEmail()!=null&&!c.getEmail().isBlank()&&!c.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw bad("Email không đúng định dạng");if(c.getPhone()!=null&&!c.getPhone().isBlank()&&!c.getPhone().matches("^[0-9+() .-]{8,20}$"))throw bad("Số điện thoại không đúng định dạng");}
 @Override protected void beforeDelete(Customer c){if(store.hasOrdersOfCustomer(c.getId()))throw conflict("Khách hàng đã có đơn hàng, không thể xóa");}
}

package vn.sportstore.controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportstore.model.Category;
import vn.sportstore.service.StoreService;
import java.util.List;
@RestController @RequestMapping("/api/categories")
public class CategoryController extends CrudControllerSupport<Category> {
 private final StoreService store; public CategoryController(StoreService store){this.store=store;}
 @GetMapping public List<Category> all(){return all(store.categories());}
 @GetMapping("/{id}") public Category one(@PathVariable Long id){return one(store.categories(),id);}
 @PostMapping public Category create(@RequestBody Category x){return create(store.categories(),x);}
 @PutMapping("/{id}") public Category update(@PathVariable Long id,@RequestBody Category x){return update(store.categories(),id,x);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){remove(store.categories(),id);}
 @Override protected void validate(Category c){if(blank(c.getName()))throw bad("Tên danh mục không được để trống");c.setName(c.getName().trim());}
 @Override protected void beforeDelete(Category c){if(store.hasProductsInCategory(c.getId()))throw conflict("Danh mục còn sản phẩm; hãy chuyển sản phẩm sang danh mục khác trước");}
}

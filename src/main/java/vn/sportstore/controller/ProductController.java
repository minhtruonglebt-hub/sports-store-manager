package vn.sportstore.controller;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.sportstore.model.Product;
import vn.sportstore.service.ProductImageStorage;
import vn.sportstore.service.StoreService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/products")
public class ProductController extends CrudControllerSupport<Product> {
    private final StoreService store;
    private final ProductImageStorage imageStorage;
    public ProductController(StoreService store, ProductImageStorage imageStorage) {
        this.store = store;
        this.imageStorage = imageStorage;
    }

    private EntityModel<Product> model(Product product) {
        return EntityModel.of(product, linkTo(methodOn(ProductController.class).one(product.getId())).withSelfRel(),
                linkTo(methodOn(ProductController.class).all()).withRel("products"));
    }

    @GetMapping
    public CollectionModel<EntityModel<Product>> all() {
        return CollectionModel.of(store.products().findAll().stream().map(this::model).toList(),
                linkTo(methodOn(ProductController.class).all()).withSelfRel());
    }

    @GetMapping("/filter")
    public Map<String, Object> filter(@RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw bad("Trang hoặc số dòng mỗi trang không hợp lệ");
        if (q.length() > 100 || (minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0))
            throw bad("Từ khóa hoặc khoảng giá không hợp lệ");
        String query = q.trim().toLowerCase(Locale.ROOT);
        List<Product> filtered = store.products().findAll().stream()
                .filter(p -> query.isBlank() || (p.getName() + " " + p.getBrand() + " " + p.getColor()).toLowerCase(Locale.ROOT).contains(query))
                .filter(p -> categoryId == null || categoryId.equals(p.getCategoryId()))
                .filter(p -> minPrice == null || p.getPrice().compareTo(minPrice) >= 0)
                .filter(p -> maxPrice == null || p.getPrice().compareTo(maxPrice) <= 0)
                .filter(p -> status == null || status.isBlank() || status.equals(p.getStatus()))
                .toList();
        int from = (int)Math.min((long)page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return Map.of("items", filtered.subList(from, to),
                "page", page, "size", size, "total", filtered.size(), "totalPages", (int)Math.ceil(filtered.size() / (double)size));
    }

    @GetMapping("/{id}") public EntityModel<Product> one(@PathVariable Long id) { return model(one(store.products(), id)); }
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadImage(@PathVariable Long id, @RequestParam("image") MultipartFile image)
            throws IOException {
        synchronized (store) {
            Product product = one(store.products(), id);
            product.setImageUrl(imageStorage.store(image));
            store.products().save(product);
            return Map.of("imageUrl", product.getImageUrl());
        }
    }

    @PostMapping public ResponseEntity<EntityModel<Product>> create(@RequestBody Product product) {
        Product saved = create(store.products(), product);
        return ResponseEntity.created(linkTo(methodOn(ProductController.class).one(saved.getId())).toUri()).body(model(saved));
    }
    @PutMapping("/{id}") public EntityModel<Product> update(@PathVariable Long id, @RequestBody Product product) {
        synchronized (store) {
            product.setImageUrl(one(store.products(), id).getImageUrl());
            return model(update(store.products(), id, product));
        }
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        synchronized (store) {
            remove(store.products(), id);
            return ResponseEntity.noContent().build();
        }
    }

    @Override protected void validate(Product p) {
        if (blank(p.getName())) throw bad("Tên sản phẩm không được để trống");
        p.setName(p.getName().trim());
        if (p.getCategoryId() == null || store.categories().findById(p.getCategoryId()).isEmpty()) throw bad("Danh mục không tồn tại");
        p.setCategory(store.categories().findById(p.getCategoryId()).orElseThrow());
        if (p.getPrice() == null || p.getPrice().signum() < 0) throw bad("Giá phải lớn hơn hoặc bằng 0");
        if (p.getQuantity() == null || p.getQuantity() < 0) throw bad("Tồn kho phải lớn hơn hoặc bằng 0");
        if (blank(p.getStatus())) p.setStatus("Đang bán");
    }
    @Override protected void beforeDelete(Product old) {
        if (store.hasDetailsOfProduct(old.getId())) throw conflict("Sản phẩm đã có trong đơn hàng; hãy chuyển sang Ngừng bán");
    }
}

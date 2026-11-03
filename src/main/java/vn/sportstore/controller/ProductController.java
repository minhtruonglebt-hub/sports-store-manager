package vn.sportstore.controller;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.sportstore.model.Product;
import vn.sportstore.model.StockMovement;
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

    @GetMapping("/low-stock")
    public Map<String, Object> lowStock() {
        List<Product> items = store.products().findAll().stream()
                .filter(product -> product.getQuantity() <= product.getMinStock())
                .filter(product -> !"Ngừng bán".equals(product.getStatus()))
                .toList();
        return Map.of("count", items.size(), "items", items);
    }

    @GetMapping("/{id}/stock-movements")
    public List<StockMovement> stockMovements(@PathVariable Long id) {
        one(store.products(), id);
        return store.stockMovements().findTop100ByProduct_IdOrderByCreatedAtDescIdDesc(id);
    }

    @PostMapping("/{id}/stock-adjustments")
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Map<String, Object> adjustStock(@PathVariable Long id, @RequestBody StockAdjustment adjustment) {
        synchronized (store) {
            Product product = one(store.products(), id);
            if (adjustment == null || adjustment.quantityChange() == null
                    || adjustment.quantityChange() == 0 || Math.abs((long) adjustment.quantityChange()) > 999999)
                throw bad("Thay đổi tồn kho phải nằm trong khoảng -999999 đến 999999 và khác 0");
            if (blank(adjustment.reason()) || adjustment.reason().trim().length() > 250)
                throw bad("Lý do điều chỉnh không được để trống và tối đa 250 ký tự");
            long newQuantity = (long) product.getQuantity() + adjustment.quantityChange();
            if (newQuantity < 0 || newQuantity > Integer.MAX_VALUE)
                throw bad("Tồn kho mới không hợp lệ");

            product.setQuantity((int) newQuantity);
            updateStockStatus(product);
            store.products().save(product);
            store.recordStockMovement(product, adjustment.quantityChange(), "ADJUSTMENT",
                    adjustment.reason().trim(), null);
            return Map.of("product", product, "quantityChange", adjustment.quantityChange());
        }
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
        Page<Product> result = store.products().search(query, categoryId, minPrice, maxPrice,
                status == null ? "" : status.trim(), PageRequest.of(page, size, Sort.by("id").ascending()));
        return Map.of("items", result.getContent(),
                "page", result.getNumber(), "size", result.getSize(),
                "total", result.getTotalElements(), "totalPages", result.getTotalPages());
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
        if (p.getMinStock() == null || p.getMinStock() < 0) throw bad("Ngưỡng cảnh báo tồn kho phải lớn hơn hoặc bằng 0");
        if (blank(p.getStatus())) p.setStatus("Đang bán");
    }
    @Override protected void beforeDelete(Product old) {
        if (store.hasDetailsOfProduct(old.getId())) throw conflict("Sản phẩm đã có trong đơn hàng; hãy chuyển sang Ngừng bán");
        if (store.stockMovements().existsByProduct_Id(old.getId())) throw conflict("Sản phẩm đã có lịch sử tồn kho; hãy chuyển sang Ngừng bán");
    }

    private static void updateStockStatus(Product product) {
        if ("Ngừng bán".equals(product.getStatus())) return;
        if (product.getQuantity() <= product.getMinStock()) product.setStatus("Sắp hết");
        else if ("Sắp hết".equals(product.getStatus())) product.setStatus("Đang bán");
    }

    public record StockAdjustment(Integer quantityChange, String reason) {}
}

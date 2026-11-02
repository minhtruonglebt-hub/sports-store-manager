package vn.sportstore.controller;

import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.server.ResponseStatusException;
import vn.sportstore.model.BaseEntity;
import java.util.List;

public abstract class CrudControllerSupport<T extends BaseEntity> {
    protected List<T> all(JpaRepository<T, Long> repository) { return repository.findAll(); }
    protected T one(JpaRepository<T, Long> repository, Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu với id " + id));
    }
    protected T create(JpaRepository<T, Long> repository, T value) {
        value.setId(null); validate(value); T saved = repository.save(value); afterSave(saved, null); return saved;
    }
    protected T update(JpaRepository<T, Long> repository, Long id, T value) {
        T old = one(repository, id); value.setId(id); validate(value); T saved = repository.save(value); afterSave(saved, old); return saved;
    }
    protected void remove(JpaRepository<T, Long> repository, Long id) {
        T old = one(repository, id); beforeDelete(old); repository.deleteById(id); afterDelete(old);
    }
    protected void validate(T value) {}
    protected void afterSave(T saved, T old) {}
    protected void beforeDelete(T old) {}
    protected void afterDelete(T old) {}
    protected static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    protected static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    protected static boolean blank(String value) { return value == null || value.isBlank(); }
}

package vn.sportstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class ProductImageStorage {
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private final Path uploadDirectory;

    public ProductImageStorage(@Value("${sportstore.upload-dir:uploads/products}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn một ảnh sản phẩm");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh không được vượt quá 5 MB");
        }

        String extension = detectExtension(image.getBytes());
        String expectedContentType = switch (extension) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> throw new IllegalStateException("Unsupported image signature");
        };
        if (!expectedContentType.equalsIgnoreCase(image.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nội dung ảnh không khớp định dạng tệp");
        }

        String filename = UUID.randomUUID() + "." + extension;
        Files.createDirectories(uploadDirectory);
        Files.write(uploadDirectory.resolve(filename), image.getBytes(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return "/uploads/products/" + filename;
    }

    private static String detectExtension(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return "png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return "jpg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') return "gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "webp";
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Chỉ hỗ trợ ảnh PNG, JPEG, GIF hoặc WebP");
    }
}

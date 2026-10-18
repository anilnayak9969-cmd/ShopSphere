package com.example.ecommerce.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.ecommerce.dtos.common.UploadResponse;
import com.example.ecommerce.exception.ApiException;

/**
 * Stores uploaded product/category images on the server's disk, OUTSIDE the web app's public
 * folders, and serves them only through {@code GET /api/images/{name}}.
 *
 * Security measures:
 * - the real file type is detected from the file's first bytes (not the name or browser header);
 * - only JPG, PNG and WEBP up to 5 MB are accepted;
 * - files get a random name, so users cannot overwrite each other's files or guess paths;
 * - names are checked against a strict pattern before reading, which blocks "../" path tricks.
 */
@Service
public class FileStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|webp)$");

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:${user.home}/shopsphere-uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create upload directory " + root, ex);
        }
    }

    public UploadResponse storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Choose an image to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Images must be 5 MB or smaller.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("UNREADABLE_FILE", "The file could not be read. Please try again.");
        }
        String extension = detectImageType(bytes);
        if (extension == null) {
            throw ApiException.badRequest("UNSUPPORTED_FILE", "Only JPG, PNG or WEBP images are allowed.");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.write(root.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save upload", ex);
        }
        return new UploadResponse("/api/images/" + name, contentTypeFor(extension), bytes.length);
    }

    public Resource loadImage(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            throw ApiException.notFound("Image not found.");
        }
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("Image not found.");
        }
        return new FileSystemResource(path);
    }

    /** Returns jpg/png/webp from the file's magic bytes, or null for anything else. */
    public static String detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    public static String contentTypeFor(String fileName) {
        if (fileName.endsWith("png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (fileName.endsWith("webp")) {
            return "image/webp";
        }
        return MediaType.IMAGE_JPEG_VALUE;
    }
}

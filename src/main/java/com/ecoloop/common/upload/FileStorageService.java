package com.ecoloop.common.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final Path rootDir;
    private final UploadMetadataRepository uploadRepository;
    private final UploadContentRepository contentRepository;

    public FileStorageService(@Value("${ecoloop.upload.dir:uploads}") String uploadDir,
                              UploadMetadataRepository uploadRepository,
                              UploadContentRepository contentRepository) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.uploadRepository = uploadRepository;
        this.contentRepository = contentRepository;
    }

    public record StoredFile(UploadMetadata metadata, byte[] content, String publicUri) {}

    @Transactional
    public StoredFile storeFile(UUID userId, String purpose, MultipartFile file, boolean allowPdf) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        byte[] content = file.getBytes();
        if (content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        ValidatedType type = detectTypeFromHeader(content, allowPdf);
        if (!"pdf".equals(type.extension())) {
            validateImageDimensions(
                content,
                "png".equals(type.extension()) ? "PNG" : "JPEG"
            );
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest unavailable", e);
        }
        String checksum = HexFormat.of().formatHex(digest.digest(content));
        String filename = UUID.randomUUID() + "." + type.extension();

        UploadMetadata metadata = new UploadMetadata(
            userId,
            purpose,
            null,
            file.getOriginalFilename() != null ? file.getOriginalFilename() : filename,
            type.mimeType(),
            content.length,
            checksum
        );

        metadata = uploadRepository.save(metadata);
        contentRepository.save(new UploadContent(metadata.getId(), content));

        String publicUri = "/api/uploads/" + metadata.getId();
        return new StoredFile(metadata, content, publicUri);
    }

    public ResponseEntity<Resource> serveUpload(UploadMetadata metadata) throws IOException {
        Resource resource;
        long lastModified;
        byte[] content = contentRepository.findById(metadata.getId())
            .map(UploadContent::getContent)
            .orElse(null);

        if (content != null) {
            resource = new ByteArrayResource(content);
            lastModified = metadata.getCreatedAt().toEpochMilli();
        } else {
            String storagePath = metadata.getStoragePath();
            if (storagePath == null || storagePath.isBlank()) {
                throw new NoSuchElementException("File content not found");
            }
            Path filePath = Paths.get(storagePath).toAbsolutePath().normalize();
            if (!filePath.startsWith(rootDir) || !Files.isRegularFile(filePath)) {
                throw new NoSuchElementException("File not found on storage");
            }
            resource = new FileSystemResource(filePath.toFile());
            lastModified = Files.getLastModifiedTime(filePath).toMillis();
        }

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(metadata.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
            .lastModified(lastModified)
            .body(resource);
    }

    public Path getRootDir() {
        return rootDir;
    }

    private ValidatedType detectTypeFromHeader(byte[] bytes, boolean allowPdf) {
        if (bytes == null || bytes.length < 4) {
            throw new IllegalArgumentException("File is corrupted or too small");
        }

        if (allowPdf && bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return new ValidatedType("application/pdf", "pdf");
        }

        if (bytes.length >= 8 &&
            (bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50 &&
            (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47 &&
            (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A &&
            (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return new ValidatedType("image/png", "png");
        }

        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new ValidatedType("image/jpeg", "jpg");
        }

        throw new IllegalArgumentException(allowPdf
            ? "File format not supported. Only JPEG, PNG, and PDF files are permitted."
            : "Image format not supported. Only valid JPEG and PNG images are permitted.");
    }

    private void validateImageDimensions(byte[] content, String formatName) {
        try (InputStream in = new ByteArrayInputStream(content);
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new IllegalArgumentException("Unable to open " + formatName + " image stream");
            }
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Corrupted " + formatName + " image data");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 10 || height < 10 || width > 10000 || height > 10000) {
                    throw new IllegalArgumentException("Image dimensions out of bounds: " + width + "x" + height);
                }
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode " + formatName + " image", e);
        }
    }

    private record ValidatedType(String mimeType, String extension) {}
}

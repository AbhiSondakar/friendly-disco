package com.ecoloop.common.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int HEADER_PEEK_BYTES = 16;

    private final Path rootDir;
    private final UploadMetadataRepository uploadRepository;

    public FileStorageService(@Value("${ecoloop.upload.dir:uploads}") String uploadDir,
                              UploadMetadataRepository uploadRepository) throws IOException {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.uploadRepository = uploadRepository;
        Files.createDirectories(this.rootDir);
    }

    public record StoredFile(UploadMetadata metadata, Path absolutePath, String publicUri) {}

    @Transactional
    public StoredFile storeFile(UUID userId, String purpose, MultipartFile file, boolean allowPdf) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest unavailable", e);
        }

        Path subDir = rootDir.resolve(purpose).normalize();
        if (!subDir.startsWith(rootDir)) {
            throw new IllegalArgumentException("Invalid storage directory path");
        }
        Files.createDirectories(subDir);

        ValidatedType type;
        Path targetPath;
        String filename;
        long bytesCopied;

        try (InputStream rawIn = file.getInputStream();
             BufferedInputStream buffered = new BufferedInputStream(rawIn)) {

            buffered.mark(HEADER_PEEK_BYTES + 1);
            byte[] header = buffered.readNBytes(HEADER_PEEK_BYTES);
            buffered.reset();

            type = detectTypeFromHeader(header, allowPdf);

            filename = UUID.randomUUID() + "." + type.extension();
            targetPath = subDir.resolve(filename).normalize();
            if (!targetPath.startsWith(rootDir)) {
                throw new IllegalArgumentException("Invalid file destination path");
            }

            DigestInputStream digestIn = new DigestInputStream(buffered, digest);
            try {
                bytesCopied = Files.copy(digestIn, targetPath);
            } catch (IOException e) {
                log.error("Failed to write uploaded file to disk: {}", e.getMessage());
                throw e;
            }
        }

        String checksum = HexFormat.of().formatHex(digest.digest());

        if (bytesCopied > MAX_FILE_SIZE) {
            Files.deleteIfExists(targetPath);
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }

        if (!"pdf".equals(type.extension())) {
            try {
                validateImageDimensions(targetPath, "png".equals(type.extension()) ? "PNG" : "JPEG");
            } catch (RuntimeException e) {
                Files.deleteIfExists(targetPath);
                throw e;
            }
        }

        UploadMetadata metadata = new UploadMetadata(
            userId,
            purpose,
            targetPath.toString(),
            file.getOriginalFilename() != null ? file.getOriginalFilename() : filename,
            type.mimeType(),
            bytesCopied,
            checksum
        );

        try {
            metadata = uploadRepository.save(metadata);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ex) {
                log.warn("Failed to clean up newly written file after database save failure: {}", ex.getMessage());
            }
            throw e;
        }

        String publicUri = "/api/uploads/" + metadata.getId();
        return new StoredFile(metadata, targetPath, publicUri);
    }

    public ResponseEntity<Resource> serveUpload(UploadMetadata metadata) throws IOException {
        Path filePath = Paths.get(metadata.getStoragePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(rootDir) || !Files.exists(filePath)) {
            throw new NoSuchElementException("File not found on storage");
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
            .lastModified(Files.getLastModifiedTime(filePath).toMillis())
            .body(new FileSystemResource(filePath.toFile()));
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

    private void validateImageDimensions(Path imagePath, String formatName) {
        try (InputStream in = Files.newInputStream(imagePath);
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

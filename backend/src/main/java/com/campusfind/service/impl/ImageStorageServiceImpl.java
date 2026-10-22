package com.campusfind.service.impl;

import com.campusfind.dto.UploadResponse;
import com.campusfind.exception.FileStorageException;
import com.campusfind.exception.InvalidFileException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.service.ImageStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implementation of ImageStorageService ensuring safe filesystem storage,
 * validation of file headers, strict extension whitelisting, and path traversal prevention.
 */
@Service
public class ImageStorageServiceImpl implements ImageStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private static final Map<String, String> EXTENSION_TO_MIME = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp|gif)$", Pattern.CASE_INSENSITIVE);

    private final Path storageLocation;

    public ImageStorageServiceImpl(@Value("${campusfind.upload.dir:uploads/images}") String uploadDir) {
        this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (IOException e) {
            throw new FileStorageException("Failed to initialize upload directory: " + this.storageLocation, e);
        }
    }

    @Override
    public UploadResponse storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select an image file to upload.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException(String.format(
                    "File size (%d bytes) exceeds the maximum allowed limit of 5MB.", file.getSize()));
        }

        // 1. Validate original file extension
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileException(
                    "Invalid file type. Only JPEG, PNG, WEBP, and GIF images are allowed.");
        }

        // 2. Validate declared Content-Type header if present
        String contentType = file.getContentType();
        if (contentType != null && !contentType.trim().isEmpty() && !contentType.equals("application/octet-stream")) {
            String lowerContentType = contentType.toLowerCase(Locale.ROOT);
            if (!lowerContentType.startsWith("image/") && !lowerContentType.equals("application/octet-stream")) {
                throw new InvalidFileException("Uploaded file does not have an image MIME type: " + contentType);
            }
        }

        // 3. Inspect magic bytes to verify genuine image content (prevent extension spoofing)
        validateMagicBytes(file, extension);

        // 4. Generate random UUID-based filename (never trust or use user-supplied filename on disk)
        String safeExtension = extension.equals("jpeg") ? "jpg" : extension;
        String generatedFilename = "img_" + UUID.randomUUID().toString().replace("-", "") + "." + safeExtension;

        // 5. Verify resolved path stays strictly within the target storage directory
        Path destination = this.storageLocation.resolve(generatedFilename).normalize();
        if (!destination.startsWith(this.storageLocation)) {
            throw new InvalidFileException("Security violation: Path traversal attempt detected.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to save image file to disk.", e);
        }

        // 6. Return response with relative public web path
        String publicUrl = "/api/images/" + generatedFilename;
        String resolvedMimeType = EXTENSION_TO_MIME.getOrDefault(safeExtension, "image/jpeg");

        return new UploadResponse(generatedFilename, publicUrl, resolvedMimeType, file.getSize());
    }

    @Override
    public Resource loadImageAsResource(String filename) {
        if (filename == null || !SAFE_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new ResourceNotFoundException("Invalid or unsafe image filename requested.");
        }

        try {
            Path filePath = this.storageLocation.resolve(filename).normalize();

            // Strict path containment check
            if (!filePath.startsWith(this.storageLocation)) {
                throw new ResourceNotFoundException("Image not found: " + filename);
            }

            if (!Files.exists(filePath) || !Files.isReadable(filePath) || Files.isDirectory(filePath)) {
                throw new ResourceNotFoundException("Image not found: " + filename);
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Image not found: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Image not found: " + filename);
        }
    }

    @Override
    public MediaType determineMediaType(String filename) {
        String ext = extractExtension(filename);
        if (ext == null) {
            return MediaType.IMAGE_JPEG;
        }
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }

    @Override
    public Path getStorageLocation() {
        return this.storageLocation;
    }

    /**
     * Extracts lowercase file extension without the leading dot.
     */
    private String extractExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Inspects the first 12 bytes of the file stream against known image signatures.
     */
    private void validateMagicBytes(MultipartFile file, String extension) {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.read(header);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read image stream for validation.", e);
        }

        if (bytesRead < 4) {
            throw new InvalidFileException("Corrupted or empty image file.");
        }

        boolean valid = false;
        switch (extension) {
            case "jpg", "jpeg" -> {
                // JPEG starts with FF D8 FF
                valid = (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);
            }
            case "png" -> {
                // PNG starts with 89 50 4E 47 (\x89PNG)
                valid = (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                         header[2] == (byte) 0x4E && header[3] == (byte) 0x47);
            }
            case "gif" -> {
                // GIF starts with GIF8 (47 49 46 38)
                valid = (header[0] == 0x47 && header[1] == 0x49 &&
                         header[2] == 0x46 && header[3] == 0x38);
            }
            case "webp" -> {
                // WEBP starts with RIFF (52 49 46 46) and bytes 8..11 are WEBP (57 45 42 50)
                if (bytesRead >= 12) {
                    boolean isRiff = (header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46);
                    boolean isWebp = (header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50);
                    valid = isRiff && isWebp;
                }
            }
        }

        if (!valid) {
            throw new InvalidFileException(
                    "Invalid image format. The file header does not match a valid " + extension.toUpperCase() + " image.");
        }
    }
}

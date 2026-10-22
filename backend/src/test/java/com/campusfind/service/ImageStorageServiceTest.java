package com.campusfind.service;

import com.campusfind.dto.UploadResponse;
import com.campusfind.exception.InvalidFileException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.service.impl.ImageStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and security tests for ImageStorageServiceImpl.
 */
class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private ImageStorageService imageStorageService;

    // Standard valid magic byte signatures
    private static final byte[] VALID_JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};
    private static final byte[] VALID_PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    private static final byte[] VALID_GIF_BYTES = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00};
    private static final byte[] VALID_WEBP_BYTES = new byte[]{
            0x52, 0x49, 0x46, 0x46, // "RIFF"
            0x24, 0x00, 0x00, 0x00, // file size
            0x57, 0x45, 0x42, 0x50  // "WEBP"
    };

    @BeforeEach
    void setUp() {
        imageStorageService = new ImageStorageServiceImpl(tempDir.toString());
    }

    @Test
    @DisplayName("Valid JPEG upload succeeds and generates unique safe filename and public URL")
    void testStoreValidJpegImageSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );

        UploadResponse response = imageStorageService.storeImage(file);

        assertNotNull(response);
        assertNotNull(response.getFileName());
        assertTrue(response.getFileName().startsWith("img_"));
        assertTrue(response.getFileName().endsWith(".jpg"));
        assertEquals("/api/images/" + response.getFileName(), response.getImageUrl());
        assertEquals("image/jpeg", response.getContentType());
        assertEquals(VALID_JPEG_BYTES.length, response.getFileSize());

        // Verify physical file was written to disk
        Path savedPath = tempDir.resolve(response.getFileName());
        assertTrue(Files.exists(savedPath));
    }

    @Test
    @DisplayName("Valid PNG upload succeeds with correct content-type and png extension")
    void testStoreValidPngImageSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "screenshot.png",
                "image/png",
                VALID_PNG_BYTES
        );

        UploadResponse response = imageStorageService.storeImage(file);

        assertNotNull(response);
        assertTrue(response.getFileName().endsWith(".png"));
        assertEquals("image/png", response.getContentType());
        assertTrue(Files.exists(tempDir.resolve(response.getFileName())));
    }

    @Test
    @DisplayName("Valid WebP upload succeeds")
    void testStoreValidWebpImageSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "graphic.webp",
                "image/webp",
                VALID_WEBP_BYTES
        );

        UploadResponse response = imageStorageService.storeImage(file);

        assertNotNull(response);
        assertTrue(response.getFileName().endsWith(".webp"));
        assertEquals("image/webp", response.getContentType());
    }

    @Test
    @DisplayName("Valid GIF upload succeeds")
    void testStoreValidGifImageSuccess() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "animation.gif",
                "image/gif",
                VALID_GIF_BYTES
        );

        UploadResponse response = imageStorageService.storeImage(file);

        assertNotNull(response);
        assertTrue(response.getFileName().endsWith(".gif"));
        assertEquals("image/gif", response.getContentType());
    }

    @Test
    @DisplayName("Uploading null or empty file throws InvalidFileException")
    void testStoreEmptyFileThrowsInvalidFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        InvalidFileException ex1 = assertThrows(InvalidFileException.class,
                () -> imageStorageService.storeImage(null));
        assertTrue(ex1.getMessage().contains("Please select an image"));

        InvalidFileException ex2 = assertThrows(InvalidFileException.class,
                () -> imageStorageService.storeImage(emptyFile));
        assertTrue(ex2.getMessage().contains("Please select an image"));
    }

    @Test
    @DisplayName("Uploading file with prohibited extension throws InvalidFileException")
    void testStoreInvalidExtensionThrowsInvalidFileException() {
        MockMultipartFile executable = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> imageStorageService.storeImage(executable));
        assertTrue(ex.getMessage().contains("Invalid file type"));

        MockMultipartFile shellScript = new MockMultipartFile(
                "file",
                "script.sh",
                "text/x-sh",
                "#!/bin/bash\necho test\n".getBytes()
        );

        assertThrows(InvalidFileException.class, () -> imageStorageService.storeImage(shellScript));
    }

    @Test
    @DisplayName("Uploading file with spoofed extension but invalid header bytes throws InvalidFileException")
    void testStoreSpoofedExtensionThrowsInvalidFileException() {
        // Named .jpg but containing plain text HTML or script
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "file",
                "fake-photo.jpg",
                "image/jpeg",
                "<html><body>Not an image</body></html>".getBytes()
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> imageStorageService.storeImage(spoofedFile));
        assertTrue(ex.getMessage().contains("Invalid image format"));
    }

    @Test
    @DisplayName("Uploading oversized file (> 5MB) throws InvalidFileException")
    void testStoreOversizedFileThrowsInvalidFileException() {
        // Construct a file that declares size > 5MB
        byte[] oversizedBytes = new byte[(int) ImageStorageServiceImpl.MAX_FILE_SIZE_BYTES + 1024];
        System.arraycopy(VALID_JPEG_BYTES, 0, oversizedBytes, 0, VALID_JPEG_BYTES.length);

        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "huge.jpg",
                "image/jpeg",
                oversizedBytes
        );

        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> imageStorageService.storeImage(largeFile));
        assertTrue(ex.getMessage().contains("exceeds the maximum allowed limit"));
    }

    @Test
    @DisplayName("Loading stored image as Resource returns readable file")
    void testLoadResourceSuccess() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );

        UploadResponse response = imageStorageService.storeImage(file);
        Resource resource = imageStorageService.loadImageAsResource(response.getFileName());

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        assertEquals(VALID_JPEG_BYTES.length, resource.contentLength());
    }

    @Test
    @DisplayName("Loading non-existent image throws ResourceNotFoundException")
    void testLoadResourceNotFoundThrowsException() {
        assertThrows(ResourceNotFoundException.class,
                () -> imageStorageService.loadImageAsResource("img_doesnotexist123.jpg"));
    }

    @Test
    @DisplayName("Path traversal attempts in filename throw ResourceNotFoundException")
    void testLoadResourcePathTraversalBlocked() {
        assertThrows(ResourceNotFoundException.class,
                () -> imageStorageService.loadImageAsResource("../secret.jpg"));
        assertThrows(ResourceNotFoundException.class,
                () -> imageStorageService.loadImageAsResource("..\\secret.jpg"));
        assertThrows(ResourceNotFoundException.class,
                () -> imageStorageService.loadImageAsResource("../../etc/passwd"));
        assertThrows(ResourceNotFoundException.class,
                () -> imageStorageService.loadImageAsResource("sub/folder.jpg"));
    }

    @Test
    @DisplayName("Determine correct MediaType based on extension")
    void testDetermineMediaType() {
        assertEquals(MediaType.IMAGE_JPEG, imageStorageService.determineMediaType("img_123.jpg"));
        assertEquals(MediaType.IMAGE_JPEG, imageStorageService.determineMediaType("img_123.jpeg"));
        assertEquals(MediaType.IMAGE_PNG, imageStorageService.determineMediaType("img_123.png"));
        assertEquals(MediaType.IMAGE_GIF, imageStorageService.determineMediaType("img_123.gif"));
        assertEquals(MediaType.parseMediaType("image/webp"), imageStorageService.determineMediaType("img_123.webp"));
    }
}

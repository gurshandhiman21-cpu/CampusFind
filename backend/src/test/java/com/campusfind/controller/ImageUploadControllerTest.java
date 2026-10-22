package com.campusfind.controller;

import com.campusfind.dto.UploadResponse;
import com.campusfind.exception.FileStorageException;
import com.campusfind.exception.GlobalExceptionHandler;
import com.campusfind.exception.InvalidFileException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.service.ImageStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for ImageUploadController endpoints:
 * - POST /api/images/upload
 * - POST /api/images
 * - GET /api/images/{filename}
 */
@WebMvcTest(ImageUploadController.class)
@Import(GlobalExceptionHandler.class)
class ImageUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageStorageService imageStorageService;

    private static final byte[] VALID_JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    @DisplayName("POST /api/images/upload with valid file returns 201 Created and JSON payload")
    void testUploadValidImageSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "calculator.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );

        UploadResponse response = new UploadResponse("img_12345.jpg", "/api/images/img_12345.jpg", "image/jpeg", 4L);
        when(imageStorageService.storeImage(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/images/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.fileName").value("img_12345.jpg"))
                .andExpect(jsonPath("$.imageUrl").value("/api/images/img_12345.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.fileSize").value(4));
    }

    @Test
    @DisplayName("POST /api/images alias with valid file returns 201 Created")
    void testUploadValidImageAliasSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        UploadResponse response = new UploadResponse("img_99999.png", "/api/images/img_99999.png", "image/png", 4L);
        when(imageStorageService.storeImage(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/images").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("img_99999.png"))
                .andExpect(jsonPath("$.imageUrl").value("/api/images/img_99999.png"));
    }

    @Test
    @DisplayName("POST /api/images/upload with invalid file type returns 400 Bad Request")
    void testUploadInvalidFileTypeReturns400() throws Exception {
        MockMultipartFile scriptFile = new MockMultipartFile(
                "file",
                "exploit.sh",
                "text/x-sh",
                "#!/bin/bash".getBytes()
        );

        when(imageStorageService.storeImage(any()))
                .thenThrow(new InvalidFileException("Invalid file type. Only JPEG, PNG, WEBP, and GIF images are allowed."));

        mockMvc.perform(multipart("/api/images/upload").file(scriptFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid file type. Only JPEG, PNG, WEBP, and GIF images are allowed."));
    }

    @Test
    @DisplayName("POST /api/images/upload with empty file returns 400 Bad Request")
    void testUploadEmptyFileReturns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        when(imageStorageService.storeImage(any()))
                .thenThrow(new InvalidFileException("Please select an image file to upload."));

        mockMvc.perform(multipart("/api/images/upload").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Please select an image file to upload."));
    }

    @Test
    @DisplayName("POST /api/images/upload with oversized file returns 400 Bad Request")
    void testUploadOversizedFileReturns400() throws Exception {
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                new byte[]{0x01}
        );

        when(imageStorageService.storeImage(any()))
                .thenThrow(new InvalidFileException("File size exceeds the maximum allowed limit of 5MB."));

        mockMvc.perform(multipart("/api/images/upload").file(largeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("File size exceeds the maximum allowed limit of 5MB."));
    }

    @Test
    @DisplayName("POST /api/images/upload when storage fails returns 500 Internal Server Error")
    void testUploadStorageFailureReturns500() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
        );

        when(imageStorageService.storeImage(any()))
                .thenThrow(new FileStorageException("Failed to save image file to disk."));

        mockMvc.perform(multipart("/api/images/upload").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Failed to save image file to disk."));
    }

    @Test
    @DisplayName("GET /api/images/{filename} returns 200 OK with proper headers and body")
    void testGetImageSuccess() throws Exception {
        Resource sampleResource = new ByteArrayResource(VALID_JPEG_BYTES);
        when(imageStorageService.loadImageAsResource("img_abc123.jpg")).thenReturn(sampleResource);
        when(imageStorageService.determineMediaType("img_abc123.jpg")).thenReturn(MediaType.IMAGE_JPEG);

        mockMvc.perform(get("/api/images/img_abc123.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(VALID_JPEG_BYTES));
    }

    @Test
    @DisplayName("GET /api/images/{filename} for non-existent image returns 404 Not Found")
    void testGetImageNotFound() throws Exception {
        when(imageStorageService.loadImageAsResource("img_notfound.jpg"))
                .thenThrow(new ResourceNotFoundException("Image not found: img_notfound.jpg"));

        mockMvc.perform(get("/api/images/img_notfound.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Image not found: img_notfound.jpg"));
    }
}

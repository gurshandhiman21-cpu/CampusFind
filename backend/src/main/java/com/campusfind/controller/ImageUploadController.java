package com.campusfind.controller;

import com.campusfind.dto.UploadResponse;
import com.campusfind.service.ImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Dedicated REST controller for uploading and serving item photographs.
 * Endpoints:
 * - POST /api/images/upload (or POST /api/images): Uploads an image safely.
 * - GET /api/images/{filename}: Serves stored image resources safely with cache headers.
 */
@RestController
@RequestMapping("/api/images")
public class ImageUploadController {

    private final ImageStorageService imageStorageService;

    public ImageUploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * POST /api/images/upload
     * Accepts a multipart image file, validates content/type/size, and saves it securely.
     */
    @PostMapping(value = {"/upload", ""})
    public ResponseEntity<UploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        UploadResponse response = imageStorageService.storeImage(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/images/{filename}
     * Retrieves and streams the stored image file safely.
     */
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        Resource resource = imageStorageService.loadImageAsResource(filename);
        MediaType mediaType = imageStorageService.determineMediaType(filename);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}

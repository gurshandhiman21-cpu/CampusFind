package com.campusfind.service;

import com.campusfind.dto.UploadResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Service contract for safe image storage, retrieval, and validation.
 */
public interface ImageStorageService {

    /**
     * Validates and persists an uploaded image file securely on disk.
     * Prevents arbitrary file uploads, path traversal, and malicious file content.
     *
     * @param file the multipart file from the client
     * @return UploadResponse containing the public image URL and metadata
     */
    UploadResponse storeImage(MultipartFile file);

    /**
     * Resolves and loads an image as a Spring Resource for HTTP streaming.
     * Validates filename strictly to prevent path traversal attempts.
     *
     * @param filename the safe image filename
     * @return Resource representing the file on disk
     */
    Resource loadImageAsResource(String filename);

    /**
     * Determines the appropriate HTTP MediaType based on image file extension.
     *
     * @param filename the image filename
     * @return matching MediaType (e.g. MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG)
     */
    MediaType determineMediaType(String filename);

    /**
     * Returns the absolute normalized root directory where images are stored.
     *
     * @return storage directory Path
     */
    Path getStorageLocation();
}

package com.campusfind.dto;

/**
 * Response payload returned upon successful image upload.
 * Provides public URL and metadata without exposing internal filesystem paths.
 */
public class UploadResponse {

    private String fileName;
    private String imageUrl;
    private String contentType;
    private long fileSize;

    public UploadResponse() {
    }

    public UploadResponse(String fileName, String imageUrl, String contentType, long fileSize) {
        this.fileName = fileName;
        this.imageUrl = imageUrl;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}

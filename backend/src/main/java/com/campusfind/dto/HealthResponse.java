package com.campusfind.dto;

/**
 * Data Transfer Object for the health status endpoint response.
 */
public class HealthResponse {

    private String status;
    private String application;

    public HealthResponse() {
    }

    public HealthResponse(String status, String application) {
        this.status = status;
        this.application = application;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }
}

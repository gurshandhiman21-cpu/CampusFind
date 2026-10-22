/**
 * CampusFind API Client
 * Central communication layer between the frontend UI and Spring Boot REST backend.
 */
const CampusFindAPI = (function () {
    // Local development endpoint for Spring Boot backend
    const API_BASE_URL = "http://localhost:8080/api";

    /**
     * Unified fetch wrapper with error handling, timeout, and JSON decoding.
     * @param {string} endpoint - Relative path (e.g., "/reports")
     * @param {object} options - Fetch options (method, headers, body)
     * @returns {Promise<any>}
     */
    async function request(endpoint, options = {}) {
        const url = `${API_BASE_URL}${endpoint}`;
        const headers = {
            "Accept": "application/json",
            ...(options.headers || {})
        };

        if (options.body && typeof options.body === "object" && !(options.body instanceof FormData)) {
            headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(options.body);
        }

        try {
            const response = await fetch(url, {
                ...options,
                headers
            });

            // Handle HTTP errors
            if (!response.ok) {
                let errorMessage = `Server responded with error status ${response.status} (${response.statusText})`;
                let fieldErrors = null;
                try {
                    const errorJson = await response.json();
                    if (errorJson.message) {
                        errorMessage = errorJson.message;
                    }
                    if (errorJson.fieldErrors) {
                        fieldErrors = errorJson.fieldErrors;
                    }
                } catch (e) {
                    // Response body was not JSON; use default message
                }

                const err = new Error(errorMessage);
                err.status = response.status;
                err.fieldErrors = fieldErrors;
                throw err;
            }

            // Handle 204 No Content
            if (response.status === 204) {
                return null;
            }

            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                return await response.json();
            }
            return await response.text();
        } catch (error) {
            // Distinguish network connection failures (backend offline / server down)
            if (!error.status) {
                error.message = "Unable to connect to the CampusFind backend at " + API_BASE_URL + 
                                ". Please verify that the Spring Boot server is running on port 8080.";
            }
            throw error;
        }
    }

    return {
        /**
         * Returns the active base API URL.
         */
        getBaseUrl: function () {
            return API_BASE_URL;
        },

        /**
         * Verifies backend connectivity.
         * GET /api/health
         */
        checkHealth: function () {
            return request("/health");
        },

        /**
         * Retrieves reports from backend with optional filters.
         * GET /api/reports?type=...&category=...&location=...&status=...
         * @param {object} filters - { type, category, location, status }
         */
        getReports: function (filters = {}) {
            const params = new URLSearchParams();
            if (filters.type) {
                params.append("type", String(filters.type).toUpperCase());
            }
            if (filters.category && filters.category !== "all") {
                params.append("category", String(filters.category).trim());
            }
            if (filters.location && filters.location !== "all") {
                params.append("location", String(filters.location).trim());
            }
            if (filters.status) {
                params.append("status", String(filters.status).toUpperCase());
            }

            const queryString = params.toString();
            return request(`/reports${queryString ? `?${queryString}` : ""}`);
        },

        /**
         * Retrieves a single report by its public reportId (e.g. LST-20261018-D171).
         * GET /api/reports/{id}
         * @param {string} id - Public report tracking code
         */
        getReportById: function (id) {
            if (!id || String(id).trim() === "") {
                return Promise.reject(new Error("A report identifier is required."));
            }
            return request(`/reports/${encodeURIComponent(String(id).trim())}`);
        },

        /**
         * Creates a new report on the server.
         * POST /api/reports
         * @param {object} reportData - ReportCreateRequest compliant payload
         */
        createReport: function (reportData) {
            return request("/reports", {
                method: "POST",
                body: reportData
            });
        },

        /**
         * Uploads an image file to the dedicated backend storage endpoint.
         * POST /api/images/upload
         * Validates file size and format before transmitting.
         * @param {File} file - The selected image file object
         * @returns {Promise<{ fileName: string, imageUrl: string, contentType: string, fileSize: number }>}
         */
        uploadImage: function (file) {
            if (!file) {
                return Promise.reject(new Error("No image file selected."));
            }

            const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
            if (file.size > MAX_FILE_SIZE) {
                return Promise.reject(new Error("File size exceeds the 5MB maximum limit. Please select a smaller photo."));
            }

            const allowedMimeTypes = ["image/jpeg", "image/png", "image/webp", "image/gif"];
            const allowedExtensions = ["jpg", "jpeg", "png", "webp", "gif"];
            const ext = file.name ? file.name.split(".").pop().toLowerCase() : "";

            const hasValidMime = file.type && allowedMimeTypes.includes(file.type.toLowerCase());
            const hasValidExt = ext && allowedExtensions.includes(ext);

            if (!hasValidMime && !hasValidExt) {
                return Promise.reject(new Error("Invalid image format. Only JPG, PNG, WEBP, and GIF images are allowed."));
            }

            const formData = new FormData();
            formData.append("file", file);

            return request("/images/upload", {
                method: "POST",
                body: formData
            });
        },

        /**
         * Resolves a relative image URL returned by the backend into an absolute URL.
         * Handles both absolute http(s) URLs and relative /api/images/ paths.
         * @param {string} imageUrl - The stored image URL
         * @returns {string|null} - Accessible URL or null
         */
        resolveImageUrl: function (imageUrl) {
            if (!imageUrl || typeof imageUrl !== "string" || imageUrl.trim() === "") {
                return null;
            }
            const trimmed = imageUrl.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
            const cleanPath = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
            const rootUrl = API_BASE_URL.replace(/\/api$/, "");
            return `${rootUrl}${cleanPath}`;
        }
    };
})();

// Attach to window
window.CampusFindAPI = CampusFindAPI;

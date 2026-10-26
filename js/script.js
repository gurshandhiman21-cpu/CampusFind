// =============================================================================
// CampusFind - Core Frontend Script
// Integrated with Spring Boot REST Backend via CampusFindAPI (js/api.js)
// =============================================================================

/**
 * Escapes HTML characters to prevent XSS when inserting dynamic data into the DOM.
 * @param {string|any} str
 * @returns {string}
 */
function escapeHTML(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/**
 * Formats a raw location slug (e.g. 'block-a') into a human-readable string.
 * @param {string} loc
 * @returns {string}
 */
function formatLocation(loc) {
    if (!loc) return '';
    const map = {
        'library': 'Central Library',
        'canteen': 'Canteen',
        'block-a': 'Block A',
        'block-b': 'Block B',
        'hostel': 'Hostel',
        'sports': 'Sports Ground',
        'parking': 'Parking'
    };
    return map[loc.toLowerCase()] || loc;
}

/**
 * Capitalizes the first letter of a string.
 * @param {string} str
 * @returns {string}
 */
function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Formats a claim status into an accessible HTML badge.
 * @param {string} status
 * @returns {string}
 */
function formatClaimBadge(status) {
    const s = String(status || '').toUpperCase();
    const map = {
        'PENDING': '<span class="claim-badge pending">⏳ Pending Review</span>',
        'APPROVED': '<span class="claim-badge approved">✅ Approved</span>',
        'REJECTED': '<span class="claim-badge rejected">❌ Rejected</span>',
        'WITHDRAWN': '<span class="claim-badge withdrawn">⚪ Withdrawn</span>',
        'RECOVERED': '<span class="claim-badge recovered">🎉 Recovered</span>'
    };
    return map[s] || `<span class="claim-badge">${escapeHTML(s)}</span>`;
}

/**
 * Displays an accessible, keyboard-friendly modal dialog.
 * @param {{ title: string, bodyHtml: string, confirmText?: string, confirmClass?: string, onConfirm?: Function }} options
 */
function showModal({ title, bodyHtml, confirmText, confirmClass, onConfirm }) {
    const existing = document.getElementById("activeModalBackdrop");
    if (existing) existing.remove();

    const backdrop = document.createElement("div");
    backdrop.id = "activeModalBackdrop";
    backdrop.className = "modal-backdrop";
    backdrop.setAttribute("role", "dialog");
    backdrop.setAttribute("aria-modal", "true");

    backdrop.innerHTML = `
        <div class="modal-dialog">
            <div class="modal-header">
                <h3>${escapeHTML(title)}</h3>
                <button type="button" class="modal-close-btn" aria-label="Close dialog">&times;</button>
            </div>
            <div class="modal-body">
                ${bodyHtml}
            </div>
            <div class="modal-footer">
                <button type="button" class="btn-withdraw modal-cancel-btn">Cancel</button>
                ${confirmText ? `<button type="button" class="${confirmClass || 'btn-primary-action'} modal-confirm-btn">${escapeHTML(confirmText)}</button>` : ''}
            </div>
        </div>
    `;

    function closeModal() {
        document.removeEventListener("keydown", onKeyDown);
        backdrop.remove();
    }

    function onKeyDown(e) {
        if (e.key === "Escape") {
            closeModal();
        }
    }

    backdrop.querySelector(".modal-close-btn").addEventListener("click", closeModal);
    backdrop.querySelector(".modal-cancel-btn").addEventListener("click", closeModal);
    backdrop.addEventListener("click", function (e) {
        if (e.target === backdrop) closeModal();
    });

    const confirmBtn = backdrop.querySelector(".modal-confirm-btn");
    if (confirmBtn && onConfirm) {
        confirmBtn.addEventListener("click", async function () {
            await onConfirm(backdrop, closeModal);
        });
    }

    document.addEventListener("keydown", onKeyDown);
    document.body.appendChild(backdrop);

    const firstInput = backdrop.querySelector("input, textarea, select");
    if (firstInput) {
        firstInput.focus();
    } else if (confirmBtn) {
        confirmBtn.focus();
    }
}


// =============================================================================
// 1. REPORT FORM PAGE (pages/report.html)
// =============================================================================

const reportForm = document.getElementById("reportForm");

if (reportForm) {
    const submitButton = reportForm.querySelector("button[type='submit']");
    const imageInput = document.getElementById("itemImage");
    const previewContainer = document.getElementById("imagePreviewContainer");
    const imagePreview = document.getElementById("imagePreview");
    const imageFileInfo = document.getElementById("imageFileInfo");
    const removeImageBtn = document.getElementById("removeImageBtn");

    const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    const ALLOWED_EXTENSIONS = ["jpg", "jpeg", "png", "webp", "gif"];
    const ALLOWED_MIME_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];

    let selectedImageFile = null;

    // Helper: Reset image input and preview state
    function clearSelectedImage() {
        selectedImageFile = null;
        if (imageInput) imageInput.value = "";
        if (previewContainer) previewContainer.style.display = "none";
        if (imagePreview) imagePreview.src = "";
        if (imageFileInfo) imageFileInfo.textContent = "";
    }

    // Handle image file selection & client-side validation
    if (imageInput) {
        imageInput.addEventListener("change", function () {
            const file = imageInput.files && imageInput.files[0];
            if (!file) {
                clearSelectedImage();
                return;
            }

            // Client-side file size validation
            if (file.size > MAX_FILE_SIZE) {
                alert("The selected image exceeds the maximum permitted size of 5 MB (" + (file.size / (1024 * 1024)).toFixed(2) + " MB). Please choose a smaller image.");
                clearSelectedImage();
                return;
            }

            // Client-side file type & extension validation
            const ext = file.name ? file.name.split(".").pop().toLowerCase() : "";
            const isValidMime = file.type && ALLOWED_MIME_TYPES.includes(file.type.toLowerCase());
            const isValidExt = ext && ALLOWED_EXTENSIONS.includes(ext);

            if (!isValidMime && !isValidExt) {
                alert("Invalid file format. Please select an image file (JPG, PNG, WEBP, or GIF).");
                clearSelectedImage();
                return;
            }

            // Valid image selected
            selectedImageFile = file;

            // Display safe client preview using Object URL
            if (previewContainer && imagePreview) {
                try {
                    imagePreview.src = URL.createObjectURL(file);
                    if (imageFileInfo) {
                        const sizeKb = (file.size / 1024).toFixed(1);
                        imageFileInfo.textContent = `${file.name} (${sizeKb} KB)`;
                    }
                    previewContainer.style.display = "block";
                } catch (e) {
                    console.warn("Could not create object URL for preview:", e);
                }
            }
        });
    }

    if (removeImageBtn) {
        removeImageBtn.addEventListener("click", function () {
            clearSelectedImage();
        });
    }

    reportForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        // Retrieve field values
        const reportTypeInput = document.querySelector('input[name="reportType"]:checked');
        const itemName = document.getElementById("itemName").value.trim();
        const category = document.getElementById("category").value;
        const description = document.getElementById("description").value.trim();
        const location = document.getElementById("location").value;
        const date = document.getElementById("date").value;
        const time = document.getElementById("time") ? document.getElementById("time").value : "";
        const additionalInfo = document.getElementById("additionalInfo") ? document.getElementById("additionalInfo").value.trim() : "";

        // Client-side Validation
        if (!reportTypeInput) {
            alert("Please select whether you lost or found the item.");
            return;
        }

        if (!itemName) {
            alert("Please enter the item name.");
            return;
        }

        if (!category) {
            alert("Please select a category.");
            return;
        }

        if (!description) {
            alert("Please provide a description of the item.");
            return;
        }

        if (!location) {
            alert("Please select the location.");
            return;
        }

        if (!date) {
            alert("Please select the date.");
            return;
        }

        // Validate date is not in future
        const selectedDate = new Date(date + "T00:00:00");
        const today = new Date();
        today.setHours(23, 59, 59, 999);
        if (selectedDate > today) {
            alert("Incident date cannot be in the future.");
            return;
        }

        // Disable submit button during upload and submission to prevent duplicate actions
        const originalButtonText = submitButton ? submitButton.textContent : "Submit Report";
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Processing...";
        }

        let uploadedImageUrl = null;

        // Step 1: Upload image file first if user attached one
        if (selectedImageFile) {
            if (submitButton) submitButton.textContent = "Uploading Image...";

            try {
                const uploadResponse = await window.CampusFindAPI.uploadImage(selectedImageFile);
                if (uploadResponse && uploadResponse.imageUrl) {
                    uploadedImageUrl = uploadResponse.imageUrl;
                }
            } catch (uploadError) {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = originalButtonText;
                }
                alert("Failed to upload image:\n\n" + uploadError.message + "\n\nPlease try again or remove the image to proceed.");
                return;
            }
        }

        // Step 2: Prepare ReportCreateRequest DTO payload
        const payload = {
            type: reportTypeInput.value.toUpperCase(), // "LOST" or "FOUND"
            itemName: itemName,
            category: category,
            description: description,
            location: location,
            incidentDate: date,
            incidentTime: time ? (time.length === 5 ? time + ":00" : time) : null,
            additionalInfo: additionalInfo || null,
            imageUrl: uploadedImageUrl
        };

        if (submitButton) submitButton.textContent = "Submitting to Server...";

        try {
            // Send POST /api/reports to Spring Boot backend
            const response = await window.CampusFindAPI.createReport(payload);

            alert(
                "Report submitted successfully to the server!\n\n" +
                "Tracking Report ID: " + response.reportId + "\n" +
                "Status: " + response.status
            );

            // Also keep legacy localStorage intact if user had existing items
            try {
                const legacyReports = JSON.parse(localStorage.getItem("reports")) || [];
                legacyReports.push({
                    reportId: response.reportId,
                    type: payload.type.toLowerCase(),
                    itemName: response.itemName,
                    category: response.category,
                    description: response.description,
                    location: response.location,
                    date: response.incidentDate,
                    time: response.incidentTime,
                    additionalInfo: response.additionalInfo,
                    imageUrl: response.imageUrl,
                    status: response.status
                });
                localStorage.setItem("reports", JSON.stringify(legacyReports));
            } catch (storageErr) {
                // Non-critical local storage cache failure
            }

            // Reset form and preview
            reportForm.reset();
            clearSelectedImage();

            // Redirect user directly to the new item's details page
            window.location.href = `item.html?id=${encodeURIComponent(response.reportId)}`;

        } catch (error) {
            let errorMsg = error.message || "An unknown error occurred while submitting the report.";
            if (error.fieldErrors) {
                const fieldDetails = Object.entries(error.fieldErrors)
                    .map(([field, msg]) => `• ${field}: ${msg}`)
                    .join("\n");
                errorMsg += "\n\nField Details:\n" + fieldDetails;
            }

            alert("Failed to create report on server:\n\n" + errorMsg);
        } finally {
            if (submitButton) {
                submitButton.disabled = false;
                submitButton.textContent = originalButtonText;
            }
        }
    });
}


// =============================================================================
// 2. LOST ITEMS PAGE (pages/lost.html)
// =============================================================================

const lostItemsContainer = document.getElementById("lostItemsContainer");

if (lostItemsContainer) {
    const searchInput = document.getElementById("searchInput");
    const categoryFilter = document.getElementById("categoryFilter");
    const locationFilter = document.getElementById("locationFilter");
    const noResults = document.getElementById("noResults");

    let loadedReports = [];

    async function fetchAndRenderLostItems() {
        // Show loading state
        lostItemsContainer.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: #666;">
                <p>Loading lost items from server...</p>
            </div>
        `;
        if (noResults) noResults.style.display = "none";

        try {
            // Retrieve reports with type=LOST from backend
            loadedReports = await window.CampusFindAPI.getReports({ type: "LOST" });
            applyFiltersAndRenderLost();
        } catch (error) {
            lostItemsContainer.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: #b00020;">
                    <p><strong>Failed to load reports from server:</strong> ${escapeHTML(error.message)}</p>
                    <button id="retryLostBtn" style="margin-top: 15px; padding: 8px 18px; border-radius: 6px; background: #222; color: #fff; cursor: pointer; border: none;">
                        Retry
                    </button>
                </div>
            `;
            const retryBtn = document.getElementById("retryLostBtn");
            if (retryBtn) {
                retryBtn.addEventListener("click", fetchAndRenderLostItems);
            }
        }
    }

    function applyFiltersAndRenderLost() {
        lostItemsContainer.innerHTML = "";

        const query = searchInput ? searchInput.value.toLowerCase().trim() : "";
        const cat = categoryFilter ? categoryFilter.value.toLowerCase() : "all";
        const loc = locationFilter ? locationFilter.value.toLowerCase() : "all";

        const filtered = loadedReports.filter(report => {
            const nameMatch = (report.itemName || "").toLowerCase().includes(query);
            const descMatch = (report.description || "").toLowerCase().includes(query);
            const searchMatch = query === "" || nameMatch || descMatch;

            const categoryMatch = (cat === "all") || (report.category || "").toLowerCase() === cat;
            const locationMatch = (loc === "all") || (report.location || "").toLowerCase() === loc;

            return searchMatch && categoryMatch && locationMatch;
        });

        if (filtered.length === 0) {
            if (noResults) noResults.style.display = "block";
            return;
        }

        if (noResults) noResults.style.display = "none";

        filtered.forEach(report => {
            const card = document.createElement("article");
            card.className = "lost-item-card";

            const resolvedUrl = window.CampusFindAPI.resolveImageUrl(report.imageUrl);
            const imageHtml = resolvedUrl
                ? `<img src="${escapeHTML(resolvedUrl)}" alt="${escapeHTML(report.itemName)}" style="width: 100%; height: 200px; object-fit: cover;" onerror="this.onerror=null; this.parentElement.innerHTML='<div style=\\'height: 200px; display: flex; align-items: center; justify-content: center; color: #777; background: #eee;\\'>No Image</div>';">`
                : `<div style="height: 200px; display: flex; align-items: center; justify-content: center; color: #777; background: #eee;">No Image</div>`;

            card.innerHTML = `
                <div class="item-image">
                    ${imageHtml}
                </div>
                <div class="item-card-content">
                    <h3>${escapeHTML(report.itemName)}</h3>
                    <p><strong>Category:</strong> ${escapeHTML(capitalize(report.category))}</p>
                    <p><strong>Location:</strong> ${escapeHTML(formatLocation(report.location))}</p>
                    <p><strong>Date:</strong> ${escapeHTML(report.incidentDate)}</p>
                    <p><strong>Time:</strong> ${escapeHTML(report.incidentTime) || "Not provided"}</p>
                    <span class="status-badge lost">🔴 LOST</span>
                    <p class="report-id">Report ID: ${escapeHTML(report.reportId)}</p>
                    <a href="item.html?id=${encodeURIComponent(report.reportId)}" class="view-item-button">View Details</a>
                </div>
            `;
            lostItemsContainer.appendChild(card);
        });
    }

    if (searchInput) searchInput.addEventListener("input", applyFiltersAndRenderLost);
    if (categoryFilter) categoryFilter.addEventListener("change", applyFiltersAndRenderLost);
    if (locationFilter) locationFilter.addEventListener("change", applyFiltersAndRenderLost);

    fetchAndRenderLostItems();
}


// =============================================================================
// 3. FOUND ITEMS PAGE (pages/found.html)
// =============================================================================

const foundItemsContainer = document.getElementById("foundItemsContainer");

if (foundItemsContainer) {
    const searchInput = document.getElementById("searchInput");
    const categoryFilter = document.getElementById("categoryFilter");
    const locationFilter = document.getElementById("locationFilter");
    const noResults = document.getElementById("noResults");

    let loadedReports = [];

    async function fetchAndRenderFoundItems() {
        // Show loading state
        foundItemsContainer.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: #666;">
                <p>Loading found items from server...</p>
            </div>
        `;
        if (noResults) noResults.style.display = "none";

        try {
            // Retrieve reports with type=FOUND from backend
            loadedReports = await window.CampusFindAPI.getReports({ type: "FOUND" });
            applyFiltersAndRenderFound();
        } catch (error) {
            foundItemsContainer.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: #b00020;">
                    <p><strong>Failed to load reports from server:</strong> ${escapeHTML(error.message)}</p>
                    <button id="retryFoundBtn" style="margin-top: 15px; padding: 8px 18px; border-radius: 6px; background: #222; color: #fff; cursor: pointer; border: none;">
                        Retry
                    </button>
                </div>
            `;
            const retryBtn = document.getElementById("retryFoundBtn");
            if (retryBtn) {
                retryBtn.addEventListener("click", fetchAndRenderFoundItems);
            }
        }
    }

    function applyFiltersAndRenderFound() {
        foundItemsContainer.innerHTML = "";

        const query = searchInput ? searchInput.value.toLowerCase().trim() : "";
        const cat = categoryFilter ? categoryFilter.value.toLowerCase() : "all";
        const loc = locationFilter ? locationFilter.value.toLowerCase() : "all";

        const filtered = loadedReports.filter(report => {
            const nameMatch = (report.itemName || "").toLowerCase().includes(query);
            const descMatch = (report.description || "").toLowerCase().includes(query);
            const searchMatch = query === "" || nameMatch || descMatch;

            const categoryMatch = (cat === "all") || (report.category || "").toLowerCase() === cat;
            const locationMatch = (loc === "all") || (report.location || "").toLowerCase() === loc;

            return searchMatch && categoryMatch && locationMatch;
        });

        if (filtered.length === 0) {
            if (noResults) noResults.style.display = "block";
            return;
        }

        if (noResults) noResults.style.display = "none";

        filtered.forEach(report => {
            const card = document.createElement("article");
            card.className = "found-item-card";

            const resolvedUrl = window.CampusFindAPI.resolveImageUrl(report.imageUrl);
            const imageHtml = resolvedUrl
                ? `<img src="${escapeHTML(resolvedUrl)}" alt="${escapeHTML(report.itemName)}" style="width: 100%; height: 200px; object-fit: cover;" onerror="this.onerror=null; this.parentElement.innerHTML='<div style=\\'height: 200px; display: flex; align-items: center; justify-content: center; color: #777; background: #eee;\\'>No Image</div>';">`
                : `<div style="height: 200px; display: flex; align-items: center; justify-content: center; color: #777; background: #eee;">No Image</div>`;

            card.innerHTML = `
                <div class="item-image">
                    ${imageHtml}
                </div>
                <div class="item-card-content">
                    <h3>${escapeHTML(report.itemName)}</h3>
                    <p><strong>Category:</strong> ${escapeHTML(capitalize(report.category))}</p>
                    <p><strong>Location:</strong> ${escapeHTML(formatLocation(report.location))}</p>
                    <p><strong>Date:</strong> ${escapeHTML(report.incidentDate)}</p>
                    <p><strong>Time:</strong> ${escapeHTML(report.incidentTime) || "Not provided"}</p>
                    <span class="status-badge found">🟢 FOUND</span>
                    <p class="report-id">Report ID: ${escapeHTML(report.reportId)}</p>
                    <a href="item.html?id=${encodeURIComponent(report.reportId)}" class="view-item-button">View Details</a>
                </div>
            `;
            foundItemsContainer.appendChild(card);
        });
    }

    if (searchInput) searchInput.addEventListener("input", applyFiltersAndRenderFound);
    if (categoryFilter) categoryFilter.addEventListener("change", applyFiltersAndRenderFound);
    if (locationFilter) locationFilter.addEventListener("change", applyFiltersAndRenderFound);

    fetchAndRenderFoundItems();
}


// =============================================================================
// MODAL & CLAIM UI HELPERS
// =============================================================================

/**
 * Returns formatted HTML for claim status badges.
 * @param {string} status - PENDING, APPROVED, REJECTED, WITHDRAWN, RECOVERED
 * @returns {string} Badge HTML
 */
function formatClaimBadge(status) {
    const s = (status || "").toUpperCase();
    switch (s) {
        case "PENDING":
            return '<span class="claim-badge pending">⏳ Pending Review</span>';
        case "APPROVED":
            return '<span class="claim-badge approved">✅ Approved</span>';
        case "REJECTED":
            return '<span class="claim-badge rejected">❌ Rejected</span>';
        case "WITHDRAWN":
            return '<span class="claim-badge withdrawn">↩️ Withdrawn</span>';
        case "RECOVERED":
            return '<span class="claim-badge recovered">🎉 Handover Completed</span>';
        default:
            return `<span class="claim-badge">${escapeHTML(s)}</span>`;
    }
}

/**
 * Renders an accessible modal dialog with backdrop, escape key support, and focus management.
 * @param {object} options
 * @param {string} options.title
 * @param {string} options.bodyHtml
 * @param {string} [options.confirmText="Confirm"]
 * @param {string} [options.confirmClass="btn-primary-action"]
 * @param {function} options.onConfirm - Callback passed (backdrop, closeModal)
 */
function showModal({ title, bodyHtml, confirmText = "Confirm", confirmClass = "btn-primary-action", onConfirm }) {
    const existingModal = document.querySelector(".modal-backdrop");
    if (existingModal) existingModal.remove();

    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";
    backdrop.setAttribute("role", "dialog");
    backdrop.setAttribute("aria-modal", "true");
    backdrop.setAttribute("aria-labelledby", "modalDialogTitle");

    backdrop.innerHTML = `
        <div class="modal-dialog">
            <div class="modal-header">
                <h3 id="modalDialogTitle">${escapeHTML(title)}</h3>
                <button type="button" class="modal-close" aria-label="Close dialog">&times;</button>
            </div>
            <div class="modal-body">
                ${bodyHtml}
            </div>
            <div class="modal-footer">
                <button type="button" class="modal-cancel-btn">Cancel</button>
                <button type="button" class="modal-confirm-btn ${confirmClass}">${escapeHTML(confirmText)}</button>
            </div>
        </div>
    `;

    document.body.appendChild(backdrop);

    const closeBtn = backdrop.querySelector(".modal-close");
    const cancelBtn = backdrop.querySelector(".modal-cancel-btn");
    const confirmBtn = backdrop.querySelector(".modal-confirm-btn");

    function closeModal() {
        document.removeEventListener("keydown", handleKeydown);
        backdrop.remove();
    }

    function handleKeydown(e) {
        if (e.key === "Escape") {
            closeModal();
        }
    }

    document.addEventListener("keydown", handleKeydown);

    backdrop.addEventListener("click", function (e) {
        if (e.target === backdrop) closeModal();
    });

    closeBtn.addEventListener("click", closeModal);
    cancelBtn.addEventListener("click", closeModal);

    if (typeof onConfirm === "function") {
        confirmBtn.addEventListener("click", function () {
            confirmBtn.disabled = true;
            onConfirm(backdrop, closeModal);
        });
    }

    // Auto-focus first input or confirm button
    const firstInput = backdrop.querySelector("input, textarea, select");
    if (firstInput) {
        firstInput.focus();
    } else {
        confirmBtn.focus();
    }
}


// =============================================================================
// 4. ITEM DETAILS PAGE (pages/item.html)
// =============================================================================

const itemDetailsContainer = document.getElementById("itemDetailsContainer");

if (itemDetailsContainer) {
    const urlParams = new URLSearchParams(window.location.search);
    const reportId = urlParams.get("id");

    if (!reportId) {
        itemDetailsContainer.innerHTML = `
            <div class="no-results">
                <h2>No Report Specified</h2>
                <p>Please select an item from the listings to view its details.</p>
                <a href="lost.html" class="view-item-button">Browse Lost Items</a>
            </div>
        `;
    } else {
        async function loadAndRenderItemDetails() {
            itemDetailsContainer.innerHTML = `
                <div style="text-align: center; padding: 60px; color: #666;">
                    <p>Loading item details from server...</p>
                </div>
            `;

            try {
                // Fetch report by public reportId
                const report = await window.CampusFindAPI.getReportById(reportId);

                // Fetch current user (if logged in)
                let currentUser = null;
                try {
                    currentUser = await window.CampusFindAPI.getCurrentUser();
                } catch (e) {
                    // Guest user
                }

                const isLost = (report.type || "").toUpperCase() === "LOST";
                const isRecovered = (report.status || "").toUpperCase() === "RECOVERED";
                const isClaimPending = (report.status || "").toUpperCase() === "CLAIM_PENDING";

                let statusClass = isLost ? "lost" : "found";
                let statusBadgeText = isLost ? "🔴 LOST" : "🟢 FOUND";

                if (isRecovered) {
                    statusClass = "recovered";
                    statusBadgeText = "🎉 RECOVERED";
                } else if (isClaimPending) {
                    statusClass = "found";
                    statusBadgeText = "⏳ CLAIM PENDING";
                }

                const resolvedUrl = window.CampusFindAPI.resolveImageUrl(report.imageUrl);
                const imageHtml = resolvedUrl
                    ? `<img src="${escapeHTML(resolvedUrl)}" alt="${escapeHTML(report.itemName)}" onerror="this.onerror=null; this.parentElement.innerHTML='<div class=\\'no-image\\'>Image not available</div>';">`
                    : `<div class="no-image">No Image</div>`;

                const additionalSection = report.additionalInfo
                    ? `
                        <div class="detail-section">
                            <h3>Additional Information</h3>
                            <p>${escapeHTML(report.additionalInfo)}</p>
                        </div>
                      `
                    : "";

                // Build recovery receipt section if recovered
                let recoveryReceiptHtml = "";
                if (isRecovered) {
                    try {
                        const recovery = await window.CampusFindAPI.getRecovery(report.reportId);
                        if (recovery) {
                            recoveryReceiptHtml = `
                                <div class="recovery-receipt-card">
                                    <h3>✅ Verified Handover Receipt</h3>
                                    <p><strong>Handover Date:</strong> ${escapeHTML(recovery.recoveryDate || "Completed")}</p>
                                    <p><strong>Recorded By:</strong> ${escapeHTML(recovery.recordedByName || "Finder / Campus Staff")}</p>
                                    ${recovery.notes ? `<p><strong>Handover Notes:</strong> ${escapeHTML(recovery.notes)}</p>` : ""}
                                </div>
                            `;
                        }
                    } catch (e) {
                        recoveryReceiptHtml = `
                            <div class="recovery-receipt-card">
                                <h3>✅ Item Recovered</h3>
                                <p>This item has been successfully recovered and returned to its owner.</p>
                            </div>
                        `;
                    }
                }

                // Build Claims & Recovery Section (only for FOUND items)
                let claimWorkflowHtml = "";
                if (!isLost && !isRecovered) {
                    if (!currentUser) {
                        // Guest: Prompt to log in
                        const returnUrl = encodeURIComponent(window.location.pathname + window.location.search);
                        claimWorkflowHtml = `
                            <div class="claim-section">
                                <div class="claim-card" style="text-align: center;">
                                    <h3>Is this item yours?</h3>
                                    <p>Log in with your campus account to submit an ownership claim and verify your ownership with the finder.</p>
                                    <a href="login.html?redirect=${returnUrl}" class="btn-primary-action" style="margin-top: 10px;">
                                        Log In to Claim This Item
                                    </a>
                                </div>
                            </div>
                        `;
                    } else {
                        // User is authenticated. Determine role on this report:
                        // Try getReportClaims. If 200 OK -> user is finder or staff/admin. If 403 -> user is claimant.
                        let reportClaims = null;
                        try {
                            reportClaims = await window.CampusFindAPI.getReportClaims(report.reportId);
                        } catch (claimsErr) {
                            // 403 Forbidden: User is not finder/staff -> regular claimant
                            reportClaims = null;
                        }

                        if (reportClaims !== null) {
                            // --- FINDER / REVIEWER VIEW ---
                            const approvedClaim = reportClaims.find(c => c.status === "APPROVED");
                            let claimsListHtml = "";

                            if (reportClaims.length === 0) {
                                claimsListHtml = `
                                    <p style="color: #6b7280; font-size: 14px; margin-top: 10px;">
                                        No ownership claims have been submitted for this found item yet. Once students submit claims, they will appear here for your review.
                                    </p>
                                `;
                            } else {
                                claimsListHtml = `
                                    <div class="claims-management-list">
                                        ${reportClaims.map(claim => {
                                            const isPending = claim.status === "PENDING";
                                            const isApproved = claim.status === "APPROVED";
                                            return `
                                                <div class="claim-review-item" data-claim-id="${claim.id}">
                                                    <div class="claim-header">
                                                        <div>
                                                            <strong>${escapeHTML(claim.claimantName || "Student")}</strong>
                                                            ${claim.claimantEmail ? `<span style="color: #6b7280; font-size: 13px;"> (${escapeHTML(claim.claimantEmail)})</span>` : ""}
                                                            <div style="font-size: 12px; color: #9ca3af;">Submitted on ${escapeHTML(new Date(claim.createdAt).toLocaleDateString())}</div>
                                                        </div>
                                                        <div>
                                                            ${formatClaimBadge(claim.status)}
                                                        </div>
                                                    </div>
                                                    <div style="font-size: 14px; color: #374151; margin-bottom: 8px;">
                                                        <strong>Explanation:</strong> ${escapeHTML(claim.explanation)}
                                                    </div>
                                                    ${claim.verificationDetails ? `
                                                        <div class="claim-confidential-box">
                                                            <strong>🔒 Confidential Proof:</strong>
                                                            <p style="margin-top: 4px; color: #78350f;">${escapeHTML(claim.verificationDetails)}</p>
                                                        </div>
                                                    ` : ""}
                                                    ${claim.reviewNotes ? `
                                                        <div style="font-size: 13px; color: #4b5563; background: #f3f4f6; padding: 8px; border-radius: 4px; margin-top: 8px;">
                                                            <strong>Review Notes:</strong> ${escapeHTML(claim.reviewNotes)}
                                                        </div>
                                                    ` : ""}
                                                    ${isPending ? `
                                                        <div class="claim-review-actions">
                                                            <button type="button" class="btn-approve review-approve-btn" data-claim-id="${claim.id}" data-claimant="${escapeHTML(claim.claimantName || "Student")}">
                                                                Approve Claim
                                                            </button>
                                                            <button type="button" class="btn-reject review-reject-btn" data-claim-id="${claim.id}" data-claimant="${escapeHTML(claim.claimantName || "Student")}">
                                                                Reject Claim
                                                            </button>
                                                        </div>
                                                    ` : ""}
                                                </div>
                                            `;
                                        }).join("")}
                                    </div>
                                `;
                            }

                            const recoveryActionBanner = approvedClaim ? `
                                <div style="background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 8px; padding: 18px; margin-top: 20px; display: flex; justify-content: space-between; align-items: center; flex-wrap: gap: 15px;">
                                    <div>
                                        <h4 style="color: #1e429f; margin-bottom: 4px;">Verified Claim Approved</h4>
                                        <p style="color: #1d4ed8; font-size: 13.5px; margin: 0;">Claim for <strong>${escapeHTML(approvedClaim.claimantName || "Claimant")}</strong> is approved. Record physical handover when the item is collected.</p>
                                    </div>
                                    <button type="button" id="openRecoveryModalBtn" class="btn-recovery" style="white-space: nowrap;">
                                        Record Physical Handover &rarr;
                                    </button>
                                </div>
                            ` : "";

                            claimWorkflowHtml = `
                                <div class="claim-section">
                                    <div class="claim-card">
                                        <h3>Claims Management (Finder Review)</h3>
                                        <p>Review ownership claims submitted by students who lost this item. Examine their confidential proof to verify genuine ownership.</p>
                                        ${recoveryActionBanner}
                                        ${claimsListHtml}
                                    </div>
                                </div>
                            `;
                        } else {
                            // --- REGULAR STUDENT / CLAIMANT VIEW ---
                            let myClaims = [];
                            try {
                                myClaims = await window.CampusFindAPI.getMyClaims();
                            } catch (e) {
                                myClaims = [];
                            }

                            const myClaim = myClaims.find(c => c.reportPublicId === report.reportId || c.reportId === report.id);

                            if (myClaim) {
                                // User already has a claim on this item
                                const isPending = myClaim.status === "PENDING";
                                const isApproved = myClaim.status === "APPROVED";
                                const isRejected = myClaim.status === "REJECTED";

                                claimWorkflowHtml = `
                                    <div class="claim-section">
                                        <div class="claim-card">
                                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;">
                                                <h3>Your Ownership Claim</h3>
                                                <div>${formatClaimBadge(myClaim.status)}</div>
                                            </div>
                                            <p style="font-size: 13px; color: #6b7280;">Submitted on ${escapeHTML(new Date(myClaim.createdAt).toLocaleDateString())}</p>
                                            <div class="claim-body" style="background: #f9fafb; padding: 14px; border-radius: 8px; margin-top: 10px;">
                                                <p><strong>Your Explanation:</strong> ${escapeHTML(myClaim.explanation)}</p>
                                                ${myClaim.verificationDetails ? `<p style="margin-top: 8px;"><strong>Your Confidential Proof:</strong> ${escapeHTML(myClaim.verificationDetails)}</p>` : ""}
                                                ${myClaim.reviewNotes ? `
                                                    <div style="margin-top: 10px; font-size: 13.5px; color: #4b5563; background: #e5e7eb; padding: 10px; border-radius: 6px;">
                                                        <strong>Finder Notes:</strong> ${escapeHTML(myClaim.reviewNotes)}
                                                    </div>
                                                ` : ""}
                                            </div>
                                            ${isApproved ? `
                                                <div style="margin-top: 14px; background: #ecfdf5; border: 1px solid #a7f3d0; padding: 12px; border-radius: 6px; color: #065f46; font-size: 14px;">
                                                    🎉 <strong>Your claim has been approved!</strong> Coordinate with the finder or visit the campus reception to collect your item.
                                                </div>
                                            ` : ""}
                                            ${isRejected ? `
                                                <div style="margin-top: 14px; background: #fef2f2; border: 1px solid #fecaca; padding: 12px; border-radius: 6px; color: #991b1b; font-size: 14px;">
                                                    Your claim was not approved by the finder.
                                                </div>
                                            ` : ""}
                                            ${isPending ? `
                                                <div style="margin-top: 16px;">
                                                    <button type="button" id="withdrawMyClaimBtn" class="btn-withdraw">
                                                        Withdraw My Claim
                                                    </button>
                                                </div>
                                            ` : ""}
                                        </div>
                                    </div>
                                `;
                            } else {
                                // User has not submitted a claim on this report yet
                                claimWorkflowHtml = `
                                    <div class="claim-section">
                                        <div class="claim-card">
                                            <h3>Claim This Item</h3>
                                            <p>If this found item belongs to you, provide proof of ownership so the finder can verify your identity.</p>
                                            <form id="claimSubmitForm" class="claim-form">
                                                <div id="claimFormAlert" class="auth-alert" style="display: none;"></div>
                                                <div class="form-group">
                                                    <label for="claimExplanation">Why is this your item? *</label>
                                                    <p class="field-hint">Describe circumstances, where/when you lost it, or general identifying features (10–2000 characters).</p>
                                                    <textarea id="claimExplanation" rows="3" required placeholder="e.g. I left my black calculator in the library 2nd floor study room after my exam on Monday."></textarea>
                                                </div>
                                                <div class="form-group">
                                                    <label for="claimVerification">Confidential Verification Details *</label>
                                                    <p class="field-hint"><strong>🔒 Private to finder & admin:</strong> Specific proof only the true owner would know (e.g. serial number, lockscreen wallpaper, engravings, secret scratches, exact contents) (5–2000 characters).</p>
                                                    <textarea id="claimVerification" rows="3" required placeholder="e.g. Serial number starts with C02; has a small smiley sticker inside the protective slide case."></textarea>
                                                </div>
                                                <button type="submit" id="submitClaimBtn" class="btn-claim">
                                                    Submit Ownership Claim
                                                </button>
                                            </form>
                                        </div>
                                    </div>
                                `;
                            }
                        }
                    }
                }

                // Render Main Card
                itemDetailsContainer.innerHTML = `
                    <div class="item-details-card">
                        <!-- Image -->
                        <div class="item-details-image">
                            ${imageHtml}
                        </div>

                        <!-- Information -->
                        <div class="item-details-content">
                            <h2>${escapeHTML(report.itemName)}</h2>
                            <span class="status-badge ${statusClass}">${statusBadgeText}</span>

                            <div class="details-list">
                                <p><strong>Report ID:</strong> ${escapeHTML(report.reportId)}</p>
                                <p><strong>Category:</strong> ${escapeHTML(capitalize(report.category))}</p>
                                <p><strong>Location:</strong> ${escapeHTML(formatLocation(report.location))}</p>
                                <p><strong>Incident Date:</strong> ${escapeHTML(report.incidentDate)}</p>
                                <p><strong>Incident Time:</strong> ${escapeHTML(report.incidentTime) || "Not provided"}</p>
                                <p><strong>Status:</strong> ${escapeHTML(report.status)}</p>
                            </div>

                            <!-- Description -->
                            <div class="detail-section">
                                <h3>Description</h3>
                                <p>${escapeHTML(report.description)}</p>
                            </div>

                            <!-- Additional Info -->
                            ${additionalSection}

                            <!-- Verified Recovery Receipt (if recovered) -->
                            ${recoveryReceiptHtml}

                            <!-- Claims & Recovery Workflow -->
                            ${claimWorkflowHtml}

                            <!-- Back Navigation -->
                            <a href="${isLost ? 'lost.html' : 'found.html'}" class="view-item-button">
                                Back to ${isLost ? 'Lost' : 'Found'} Items
                            </a>
                        </div>
                    </div>
                `;

                // --- Attach Event Listeners ---

                // 1. Claim Submission Form Listener
                const claimSubmitForm = document.getElementById("claimSubmitForm");
                if (claimSubmitForm) {
                    const claimAlert = document.getElementById("claimFormAlert");
                    const submitBtn = document.getElementById("submitClaimBtn");

                    claimSubmitForm.addEventListener("submit", async function (e) {
                        e.preventDefault();
                        if (claimAlert) claimAlert.style.display = "none";

                        const explanation = document.getElementById("claimExplanation").value.trim();
                        const verificationDetails = document.getElementById("claimVerification").value.trim();

                        if (!explanation || explanation.length < 10 || explanation.length > 2000) {
                            if (claimAlert) {
                                claimAlert.className = "auth-alert error";
                                claimAlert.textContent = "Please provide an explanation of at least 10 characters.";
                                claimAlert.style.display = "block";
                            }
                            return;
                        }

                        if (!verificationDetails || verificationDetails.length < 5 || verificationDetails.length > 2000) {
                            if (claimAlert) {
                                claimAlert.className = "auth-alert error";
                                claimAlert.textContent = "Please provide confidential verification proof of at least 5 characters.";
                                claimAlert.style.display = "block";
                            }
                            return;
                        }

                        submitBtn.disabled = true;
                        const originalText = submitBtn.textContent;
                        submitBtn.textContent = "Submitting Claim...";

                        try {
                            await window.CampusFindAPI.submitClaim(report.reportId, {
                                explanation,
                                verificationDetails
                            });
                            alert("Your ownership claim has been submitted successfully! The finder will review your verification details.");
                            await loadAndRenderItemDetails();
                        } catch (err) {
                            submitBtn.disabled = false;
                            submitBtn.textContent = originalText;
                            if (claimAlert) {
                                claimAlert.className = "auth-alert error";
                                claimAlert.textContent = err.message || "Failed to submit claim. Please try again.";
                                claimAlert.style.display = "block";
                            }
                        }
                    });
                }

                // 2. Claimant Withdraw Button Listener
                const withdrawMyClaimBtn = document.getElementById("withdrawMyClaimBtn");
                if (withdrawMyClaimBtn) {
                    withdrawMyClaimBtn.addEventListener("click", function () {
                        // Find current active claim ID
                        window.CampusFindAPI.getMyClaims().then(myClaims => {
                            const claim = myClaims.find(c => c.reportPublicId === report.reportId || c.reportId === report.id);
                            if (!claim) return;

                            showModal({
                                title: "Withdraw Claim",
                                bodyHtml: `<p>Are you sure you want to withdraw your claim on <strong>${escapeHTML(report.itemName)}</strong>? This action cannot be undone.</p>`,
                                confirmText: "Yes, Withdraw Claim",
                                confirmClass: "btn-reject",
                                onConfirm: async function (backdrop, closeModal) {
                                    try {
                                        await window.CampusFindAPI.withdrawClaim(claim.id);
                                        closeModal();
                                        alert("Your claim has been withdrawn.");
                                        await loadAndRenderItemDetails();
                                    } catch (err) {
                                        alert("Failed to withdraw claim: " + err.message);
                                    }
                                }
                            });
                        });
                    });
                }

                // 3. Finder Review Buttons (Approve / Reject)
                const approveBtns = itemDetailsContainer.querySelectorAll(".review-approve-btn");
                approveBtns.forEach(btn => {
                    btn.addEventListener("click", function () {
                        const claimId = btn.getAttribute("data-claim-id");
                        const claimant = btn.getAttribute("data-claimant");

                        showModal({
                            title: "Approve Ownership Claim",
                            bodyHtml: `
                                <p>Are you sure you want to <strong>approve</strong> the claim from <strong>${escapeHTML(claimant)}</strong>?</p>
                                <div class="form-group" style="margin-top: 14px;">
                                    <label for="reviewNotesModalInput">Review Notes (Optional):</label>
                                    <textarea id="reviewNotesModalInput" rows="2" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 6px;" placeholder="e.g. Verification details match the item."></textarea>
                                </div>
                            `,
                            confirmText: "Confirm Approval",
                            confirmClass: "btn-approve",
                            onConfirm: async function (backdrop, closeModal) {
                                const notesInput = document.getElementById("reviewNotesModalInput");
                                const reviewNotes = notesInput ? notesInput.value.trim() : null;
                                try {
                                    await window.CampusFindAPI.reviewClaim(claimId, {
                                        status: "APPROVED",
                                        reviewNotes: reviewNotes || null
                                    });
                                    closeModal();
                                    alert("Claim approved successfully!");
                                    await loadAndRenderItemDetails();
                                } catch (err) {
                                    alert("Failed to approve claim: " + err.message);
                                }
                            }
                        });
                    });
                });

                const rejectBtns = itemDetailsContainer.querySelectorAll(".review-reject-btn");
                rejectBtns.forEach(btn => {
                    btn.addEventListener("click", function () {
                        const claimId = btn.getAttribute("data-claim-id");
                        const claimant = btn.getAttribute("data-claimant");

                        showModal({
                            title: "Reject Ownership Claim",
                            bodyHtml: `
                                <p>Are you sure you want to <strong>reject</strong> the claim from <strong>${escapeHTML(claimant)}</strong>?</p>
                                <div class="form-group" style="margin-top: 14px;">
                                    <label for="reviewNotesModalInput">Reason / Notes (Optional):</label>
                                    <textarea id="reviewNotesModalInput" rows="2" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 6px;" placeholder="e.g. Identifying marks did not match."></textarea>
                                </div>
                            `,
                            confirmText: "Confirm Rejection",
                            confirmClass: "btn-reject",
                            onConfirm: async function (backdrop, closeModal) {
                                const notesInput = document.getElementById("reviewNotesModalInput");
                                const reviewNotes = notesInput ? notesInput.value.trim() : null;
                                try {
                                    await window.CampusFindAPI.reviewClaim(claimId, {
                                        status: "REJECTED",
                                        reviewNotes: reviewNotes || null
                                    });
                                    closeModal();
                                    alert("Claim rejected.");
                                    await loadAndRenderItemDetails();
                                } catch (err) {
                                    alert("Failed to reject claim: " + err.message);
                                }
                            }
                        });
                    });
                });

                // 4. Record Recovery Handover Modal Listener
                const openRecoveryModalBtn = document.getElementById("openRecoveryModalBtn");
                if (openRecoveryModalBtn) {
                    openRecoveryModalBtn.addEventListener("click", async function () {
                        try {
                            const claims = await window.CampusFindAPI.getReportClaims(report.reportId);
                            const approved = claims.find(c => c.status === "APPROVED");
                            if (!approved) {
                                alert("No approved claim found for this report.");
                                return;
                            }

                            const todayStr = new Date().toISOString().split("T")[0];

                            showModal({
                                title: "Record Physical Handover",
                                bodyHtml: `
                                    <p>Confirming physical return of <strong>${escapeHTML(report.itemName)}</strong> to <strong>${escapeHTML(approved.claimantName || "Claimant")}</strong>.</p>
                                    <div style="background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px; padding: 10px; font-size: 13px; color: #92400e; margin: 12px 0;">
                                        ⚠️ <strong>Notice:</strong> This action will permanently mark the report as <strong>RECOVERED</strong> and automatically reject any other pending claims.
                                    </div>
                                    <div class="form-group" style="margin-top: 12px;">
                                        <label for="recoveryDateInput">Handover Date *</label>
                                        <input type="date" id="recoveryDateInput" value="${todayStr}" max="${todayStr}" required style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 6px;">
                                    </div>
                                    <div class="form-group" style="margin-top: 12px;">
                                        <label for="recoveryNotesInput">Handover Notes (Optional):</label>
                                        <textarea id="recoveryNotesInput" rows="2" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 6px;" placeholder="e.g. Verified student ID and handed over laptop at library desk."></textarea>
                                    </div>
                                `,
                                confirmText: "Confirm Handover & Resolve",
                                confirmClass: "btn-recovery",
                                onConfirm: async function (backdrop, closeModal) {
                                    const dateInput = document.getElementById("recoveryDateInput");
                                    const notesInput = document.getElementById("recoveryNotesInput");

                                    const recoveryDate = dateInput ? dateInput.value : todayStr;
                                    const notes = notesInput ? notesInput.value.trim() : null;

                                    if (!recoveryDate) {
                                        alert("Please select the handover date.");
                                        return;
                                    }

                                    try {
                                        await window.CampusFindAPI.recordRecovery(report.reportId, {
                                            claimId: approved.id,
                                            recoveryDate: recoveryDate,
                                            notes: notes || null
                                        });
                                        closeModal();
                                        alert("Item handover recorded successfully! The report is now marked as RECOVERED.");
                                        await loadAndRenderItemDetails();
                                    } catch (err) {
                                        alert("Failed to record recovery handover: " + err.message);
                                    }
                                }
                            });
                        } catch (err) {
                            alert("Failed to prepare recovery handover: " + err.message);
                        }
                    });
                }

            } catch (error) {
                itemDetailsContainer.innerHTML = `
                    <div class="no-results">
                        <h2>Item Not Found</h2>
                        <p>${escapeHTML(error.message || "The requested item report could not be found on the server.")}</p>
                        <a href="lost.html" class="view-item-button">Back to Items</a>
                    </div>
                `;
            }
        }

        loadAndRenderItemDetails();
    }
}


// =============================================================================
// 5. HOMEPAGE RECENT REPORTS (index.html)
// =============================================================================

const homeRecentContainer = document.querySelector(".recent-reports .item-container");

if (homeRecentContainer) {
    (async function () {
        try {
            // Fetch reports from backend
            const reports = await window.CampusFindAPI.getReports();

            if (Array.isArray(reports) && reports.length > 0) {
                homeRecentContainer.innerHTML = "";

                // Display up to 6 recent reports
                const recentSlice = reports.slice(0, 6);

                recentSlice.forEach(report => {
                    const article = document.createElement("article");
                    article.className = "item-card";

                    const isLost = (report.type || "").toUpperCase() === "LOST";
                    const statusText = isLost ? "Lost" : "Found";

                    article.innerHTML = `
                        <h3>${escapeHTML(report.itemName)}</h3>
                        <p>Category: ${escapeHTML(capitalize(report.category))}</p>
                        <p>Location: ${escapeHTML(formatLocation(report.location))}</p>
                        <p>Status: ${statusText}</p>
                        <a href="pages/item.html?id=${encodeURIComponent(report.reportId)}">View Details</a>
                    `;

                    homeRecentContainer.appendChild(article);
                });
            }
        } catch (error) {
            // Keep existing static fallback on homepage if server is offline during initial load
            console.warn("Could not load dynamic recent reports from backend:", error.message);
        }
    })();
}


// =============================================================================
// 6. GLOBAL AUTHENTICATION & NAVIGATION STATE
// =============================================================================

(async function initNavbarAuth() {
    const navLinks = document.getElementById("navLinks");
    if (!navLinks) return;

    try {
        const user = await window.CampusFindAPI.getCurrentUser();
        if (user && user.email) {
            // Remove guest login/register links
            const authLinks = navLinks.querySelectorAll(".auth-link");
            authLinks.forEach(link => link.remove());

            // Add My Claims navigation link if not already present
            if (!navLinks.querySelector('a[href*="my-claims.html"]')) {
                const isPagesDir = window.location.pathname.includes("/pages/");
                const myClaimsHref = isPagesDir ? "my-claims.html" : "pages/my-claims.html";
                const claimsLi = document.createElement("li");
                claimsLi.innerHTML = `<a href="${myClaimsHref}">My Claims</a>`;
                navLinks.appendChild(claimsLi);
            }

            // Create user greeting item
            const userLi = document.createElement("li");
            userLi.className = "nav-user";

            const greetingSpan = document.createElement("span");
            greetingSpan.className = "user-greeting";
            const displayName = escapeHTML(user.fullName || user.email.split("@")[0]);
            const roleBadge = user.role ? `<span class="user-role-badge">${escapeHTML(user.role)}</span>` : "";
            greetingSpan.innerHTML = `Hi, ${displayName}${roleBadge}`;
            userLi.appendChild(greetingSpan);

            // Create logout button item
            const logoutLi = document.createElement("li");
            const logoutBtn = document.createElement("button");
            logoutBtn.id = "navLogoutBtn";
            logoutBtn.className = "nav-logout-btn";
            logoutBtn.textContent = "Logout";
            logoutBtn.type = "button";

            logoutBtn.addEventListener("click", async function () {
                logoutBtn.disabled = true;
                logoutBtn.textContent = "Logging out...";
                try {
                    await window.CampusFindAPI.logout();
                } catch (e) {
                    console.warn("Logout error:", e);
                } finally {
                    // Refresh current view to update navigation and protected states
                    window.location.reload();
                }
            });

            logoutLi.appendChild(logoutBtn);
            navLinks.appendChild(userLi);
            navLinks.appendChild(logoutLi);
        }
    } catch (err) {
        // 401 Unauthorized or backend offline: user is guest, leave Login & Register links visible
        if (err.status !== 401) {
            console.debug("Current session check:", err.message);
        }
    }
})();


// =============================================================================
// 7. LOGIN PAGE CONTROLLER (pages/login.html)
// =============================================================================

const loginForm = document.getElementById("loginForm");

if (loginForm) {
    const emailInput = document.getElementById("loginEmail");
    const passwordInput = document.getElementById("loginPassword");
    const authAlert = document.getElementById("authAlert");
    const emailError = document.getElementById("emailError");
    const passwordError = document.getElementById("passwordError");
    const submitBtn = document.getElementById("loginSubmitBtn");

    function showAlert(message, type = "error") {
        if (!authAlert) return;
        authAlert.className = `auth-alert ${type}`;
        authAlert.textContent = message;
        authAlert.style.display = "block";
    }

    function clearAlert() {
        if (!authAlert) return;
        authAlert.className = "auth-alert";
        authAlert.textContent = "";
        authAlert.style.display = "none";
    }

    loginForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        clearAlert();
        if (emailError) emailError.classList.remove("active");
        if (passwordError) passwordError.classList.remove("active");

        const email = emailInput.value.trim().toLowerCase();
        const password = passwordInput.value;

        let hasError = false;

        // Basic client-side email format validation
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!email || !emailRegex.test(email)) {
            if (emailError) emailError.classList.add("active");
            hasError = true;
        }

        if (!password) {
            if (passwordError) passwordError.classList.add("active");
            hasError = true;
        }

        if (hasError) return;

        submitBtn.disabled = true;
        const originalText = submitBtn.textContent;
        submitBtn.textContent = "Logging in...";

        try {
            const user = await window.CampusFindAPI.login({ email, password });
            showAlert(`Welcome back, ${user.fullName || user.email}! Redirecting...`, "success");

            // Handle safe redirect query parameter
            const params = new URLSearchParams(window.location.search);
            const redirectTarget = params.get("redirect");
            let destination = "lost.html";

            if (redirectTarget && !redirectTarget.startsWith("http://") && !redirectTarget.startsWith("https://") && !redirectTarget.startsWith("//")) {
                destination = redirectTarget;
            }

            setTimeout(() => {
                window.location.href = destination;
            }, 600);
        } catch (err) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;

            if (err.status === 401) {
                showAlert("Invalid email or password. Please check your credentials and try again.", "error");
            } else if (err.status === 429) {
                showAlert("Too many failed login attempts. Please wait 5 minutes before trying again.", "error");
            } else {
                showAlert(err.message || "An unexpected error occurred during login. Please try again.", "error");
            }
        }
    });
}


// =============================================================================
// 8. REGISTRATION PAGE CONTROLLER (pages/register.html)
// =============================================================================

const registerForm = document.getElementById("registerForm");

if (registerForm) {
    const fullNameInput = document.getElementById("registerFullName");
    const emailInput = document.getElementById("registerEmail");
    const passwordInput = document.getElementById("registerPassword");
    const confirmPasswordInput = document.getElementById("registerConfirmPassword");
    const authAlert = document.getElementById("authAlert");
    const nameError = document.getElementById("nameError");
    const emailError = document.getElementById("emailError");
    const passwordError = document.getElementById("passwordError");
    const confirmPasswordError = document.getElementById("confirmPasswordError");
    const submitBtn = document.getElementById("registerSubmitBtn");

    function showAlert(message, type = "error") {
        if (!authAlert) return;
        authAlert.className = `auth-alert ${type}`;
        authAlert.textContent = message;
        authAlert.style.display = "block";
    }

    function clearAlert() {
        if (!authAlert) return;
        authAlert.className = "auth-alert";
        authAlert.textContent = "";
        authAlert.style.display = "none";
    }

    registerForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        clearAlert();
        if (nameError) nameError.classList.remove("active");
        if (emailError) emailError.classList.remove("active");
        if (passwordError) passwordError.classList.remove("active");
        if (confirmPasswordError) confirmPasswordError.classList.remove("active");

        const fullName = fullNameInput.value.trim();
        const email = emailInput.value.trim().toLowerCase();
        const password = passwordInput.value;
        const confirmPassword = confirmPasswordInput.value;

        let hasError = false;

        if (!fullName || fullName.length < 2 || fullName.length > 100) {
            if (nameError) nameError.classList.add("active");
            hasError = true;
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!email || !emailRegex.test(email) || email.length > 150) {
            if (emailError) emailError.classList.add("active");
            hasError = true;
        }

        if (!password || password.length < 8 || password.length > 128) {
            if (passwordError) passwordError.classList.add("active");
            hasError = true;
        }

        if (password !== confirmPassword) {
            if (confirmPasswordError) confirmPasswordError.classList.add("active");
            hasError = true;
        }

        if (hasError) return;

        submitBtn.disabled = true;
        const originalText = submitBtn.textContent;
        submitBtn.textContent = "Creating Account...";

        try {
            const user = await window.CampusFindAPI.register({ fullName, email, password });
            registerForm.reset();

            // Provide direct login link inside alert
            if (authAlert) {
                authAlert.className = "auth-alert success";
                authAlert.style.display = "block";
                authAlert.innerHTML = `Account created successfully for <strong>${escapeHTML(user.fullName)}</strong>! <a href="login.html" style="color: inherit; font-weight: bold; text-decoration: underline; margin-left: 8px;">Click here to log in &rarr;</a>`;
            }

            submitBtn.disabled = false;
            submitBtn.textContent = "Account Created";
        } catch (err) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;

            if (err.status === 409) {
                showAlert("An account with this email address already exists. Please log in or use a different email.", "error");
            } else if (err.status === 400 && err.fieldErrors) {
                const firstErr = Object.values(err.fieldErrors)[0];
                showAlert(firstErr || "Please verify all required fields.", "error");
            } else {
                showAlert(err.message || "An unexpected error occurred during registration.", "error");
            }
        }
    });
}


// =============================================================================
// 9. MY CLAIMS PAGE CONTROLLER (pages/my-claims.html)
// =============================================================================

const myClaimsContainer = document.getElementById("myClaimsContainer");

if (myClaimsContainer) {
    const claimsLoading = document.getElementById("claimsLoading");
    const emptyClaims = document.getElementById("emptyClaims");
    const claimsAlert = document.getElementById("claimsAlert");

    function showClaimsAlert(msg, type = "error") {
        if (!claimsAlert) return;
        claimsAlert.className = `auth-alert ${type}`;
        claimsAlert.textContent = msg;
        claimsAlert.style.display = "block";
    }

    async function loadMyClaims() {
        if (claimsLoading) claimsLoading.style.display = "block";
        if (emptyClaims) emptyClaims.style.display = "none";
        myClaimsContainer.style.display = "none";
        myClaimsContainer.innerHTML = "";

        // 1. Authenticate user session
        let currentUser = null;
        try {
            currentUser = await window.CampusFindAPI.getCurrentUser();
        } catch (e) {
            // Unauthenticated
        }

        if (!currentUser || !currentUser.email) {
            if (claimsLoading) claimsLoading.style.display = "none";
            // Redirect unauthenticated user to login with return redirect
            window.location.href = "login.html?redirect=my-claims.html";
            return;
        }

        // 2. Fetch user's claims from backend
        try {
            const claims = await window.CampusFindAPI.getMyClaims();
            if (claimsLoading) claimsLoading.style.display = "none";

            if (!Array.isArray(claims) || claims.length === 0) {
                if (emptyClaims) emptyClaims.style.display = "block";
                return;
            }

            myClaimsContainer.style.display = "grid";
            myClaimsContainer.innerHTML = "";

            claims.forEach(claim => {
                const card = document.createElement("article");
                card.className = "my-claim-card";

                const isPending = claim.status === "PENDING";
                const badgeHtml = formatClaimBadge(claim.status);
                const submittedDate = claim.createdAt ? new Date(claim.createdAt).toLocaleDateString(undefined, {
                    year: "numeric", month: "short", day: "numeric"
                }) : "Unknown";

                const targetReportId = claim.reportPublicId || claim.reportId;
                const itemLink = targetReportId ? `item.html?id=${encodeURIComponent(targetReportId)}` : "found.html";

                let withdrawBtnHtml = "";
                if (isPending) {
                    withdrawBtnHtml = `
                        <button type="button" class="btn-withdraw withdraw-claim-btn" data-claim-id="${claim.id}" data-item-name="${escapeHTML(claim.reportItemName || "item")}">
                            Withdraw Claim
                        </button>
                    `;
                }

                let reviewNotesHtml = "";
                if (claim.reviewNotes) {
                    reviewNotesHtml = `
                        <div class="claim-notes" style="margin-top: 10px; padding: 10px 14px; background: #f8fafc; border-left: 3px solid #64748b; border-radius: 4px; font-size: 13.5px; color: #334155;">
                            <strong>Reviewer Note:</strong> ${escapeHTML(claim.reviewNotes)}
                        </div>
                    `;
                }

                card.innerHTML = `
                    <div class="card-top">
                        <div>
                            <h3>
                                <a href="${itemLink}">${escapeHTML(claim.reportItemName || "Claimed Item")}</a>
                            </h3>
                            <div class="report-meta">
                                Submitted on ${submittedDate}
                                ${claim.reportCategory ? ` • Category: ${escapeHTML(capitalize(claim.reportCategory))}` : ""}
                                ${claim.reportLocation ? ` • Location: ${escapeHTML(formatLocation(claim.reportLocation))}` : ""}
                            </div>
                        </div>
                        <div>
                            ${badgeHtml}
                        </div>
                    </div>

                    <div class="claim-body">
                        <p style="margin: 0 0 8px 0;"><strong>Why is this your item:</strong> ${escapeHTML(claim.explanation)}</p>
                        ${claim.verificationDetails ? `<p style="margin: 0 0 8px 0; font-size: 13.5px; color: #475569;"><strong>🔒 Your Verification Proof:</strong> ${escapeHTML(claim.verificationDetails)}</p>` : ""}
                        ${reviewNotesHtml}
                    </div>

                    <div class="card-actions">
                        <a href="${itemLink}" class="view-item-button" style="padding: 8px 16px; font-size: 14px;">View Item Details</a>
                        ${withdrawBtnHtml}
                    </div>
                `;

                myClaimsContainer.appendChild(card);
            });

            // Attach withdraw button listeners
            const withdrawBtns = myClaimsContainer.querySelectorAll(".withdraw-claim-btn");
            withdrawBtns.forEach(btn => {
                btn.addEventListener("click", function () {
                    const claimId = btn.getAttribute("data-claim-id");
                    const itemName = btn.getAttribute("data-item-name");

                    showModal({
                        title: "Withdraw Ownership Claim",
                        bodyHtml: `<p>Are you sure you want to withdraw your claim on <strong>${escapeHTML(itemName)}</strong>? You will no longer be considered for ownership of this item.</p>`,
                        confirmText: "Yes, Withdraw Claim",
                        confirmClass: "btn-reject",
                        onConfirm: async function (backdrop, closeModal) {
                            try {
                                await window.CampusFindAPI.withdrawClaim(claimId);
                                closeModal();
                                alert("Your claim has been withdrawn.");
                                await loadMyClaims();
                            } catch (err) {
                                alert("Failed to withdraw claim: " + err.message);
                            }
                        }
                    });
                });
            });

        } catch (err) {
            if (claimsLoading) claimsLoading.style.display = "none";
            showClaimsAlert(err.message || "Failed to load your claims. Please try again later.", "error");
        }
    }

    loadMyClaims();
}

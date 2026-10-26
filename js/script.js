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
        // Show loading indicator
        itemDetailsContainer.innerHTML = `
            <div style="text-align: center; padding: 60px; color: #666;">
                <p>Loading item details from server...</p>
            </div>
        `;

        (async function () {
            try {
                // Fetch report by public reportId
                const report = await window.CampusFindAPI.getReportById(reportId);

                const isLost = (report.type || "").toUpperCase() === "LOST";
                const statusClass = isLost ? "lost" : "found";
                const statusBadgeText = isLost ? "🔴 LOST" : "🟢 FOUND";

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

                            <!-- Back Navigation -->
                            <a href="${isLost ? 'lost.html' : 'found.html'}" class="view-item-button">
                                Back to ${isLost ? 'Lost' : 'Found'} Items
                            </a>
                        </div>
                    </div>
                `;
            } catch (error) {
                itemDetailsContainer.innerHTML = `
                    <div class="no-results">
                        <h2>Item Not Found</h2>
                        <p>${escapeHTML(error.message || "The requested item report could not be found on the server.")}</p>
                        <a href="lost.html" class="view-item-button">Back to Items</a>
                    </div>
                `;
            }
        })();
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
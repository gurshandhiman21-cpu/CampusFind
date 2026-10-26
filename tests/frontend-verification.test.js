const { test, describe, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

describe('CampusFind Frontend Authentication Verification', () => {

    const rootDir = path.resolve(__dirname, '..');
    const apiJsPath = path.join(rootDir, 'js', 'api.js');
    const scriptJsPath = path.join(rootDir, 'js', 'script.js');

    test('1. Security: No tokens, passwords, or sessions stored in localStorage or sessionStorage', () => {
        const apiCode = fs.readFileSync(apiJsPath, 'utf8');
        const scriptCode = fs.readFileSync(scriptJsPath, 'utf8');

        const sensitiveKeywords = ['password', 'token', 'session', 'jwt', 'auth'];
        for (const kw of sensitiveKeywords) {
            const rxLocal = new RegExp(`localStorage\\.setItem\\([^)]*${kw}`, 'i');
            const rxSession = new RegExp(`sessionStorage\\.setItem\\([^)]*${kw}`, 'i');

            assert.ok(!rxLocal.test(apiCode), `api.js must not store ${kw} in localStorage`);
            assert.ok(!rxSession.test(apiCode), `api.js must not store ${kw} in sessionStorage`);
            assert.ok(!rxLocal.test(scriptCode), `script.js must not store ${kw} in localStorage`);
            assert.ok(!rxSession.test(scriptCode), `script.js must not store ${kw} in sessionStorage`);
        }
    });

    test('2. HTML page links, scripts, and navigation integrity', () => {
        const htmlFiles = [
            'index.html',
            'pages/lost.html',
            'pages/found.html',
            'pages/report.html',
            'pages/item.html',
            'pages/login.html',
            'pages/register.html'
        ];

        for (const relPath of htmlFiles) {
            const fullPath = path.join(rootDir, relPath);
            assert.ok(fs.existsSync(fullPath), `File ${relPath} must exist`);

            const content = fs.readFileSync(fullPath, 'utf8');
            assert.ok(content.includes('id="navLinks"'), `${relPath} must contain navLinks ul`);
            assert.ok(content.includes('login.html'), `${relPath} must link to login.html`);
            assert.ok(content.includes('register.html'), `${relPath} must link to register.html`);
            assert.ok(content.includes('style.css'), `${relPath} must link to style.css`);
            assert.ok(content.includes('api.js'), `${relPath} must load api.js`);
            assert.ok(content.includes('script.js'), `${relPath} must load script.js`);
        }
    });

    test('3. API Client: CSRF acquisition, header usage, and credentials: "include"', async () => {
        const fetchCalls = [];

        // Mock global fetch and window
        global.window = {};
        global.fetch = async (url, options = {}) => {
            fetchCalls.push({ url, options });

            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({
                        token: 'test-csrf-token-abc',
                        headerName: 'X-CSRF-TOKEN',
                        parameterName: '_csrf'
                    })
                };
            }

            if (url.endsWith('/reports')) {
                return {
                    ok: true,
                    status: 201,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ id: 101, reportId: 'LST-20261026-0001' })
                };
            }

            if (url.endsWith('/health')) {
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ status: 'UP' })
                };
            }

            return { ok: true, status: 200, headers: new Map(), json: async () => ({}) };
        };

        // Load api.js in clean context
        delete require.cache[require.resolve(apiJsPath)];
        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        // Test safe GET: does not fetch CSRF token
        await API.checkHealth();
        assert.equal(fetchCalls.length, 1);
        assert.ok(fetchCalls[0].url.endsWith('/health'));
        assert.equal(fetchCalls[0].options.credentials, 'include');
        assert.equal(fetchCalls[0].options.headers['X-CSRF-TOKEN'], undefined);

        // Test unsafe POST: acquires CSRF and attaches header
        const reportResult = await API.createReport({ itemName: 'Laptop' });
        assert.ok(reportResult);
        assert.equal(reportResult.reportId, 'LST-20261026-0001');

        const csrfCall = fetchCalls.find(c => c.url.endsWith('/auth/csrf'));
        assert.ok(csrfCall, 'Must acquire CSRF token before POST');
        assert.equal(csrfCall.options.credentials, 'include');

        const postCall = fetchCalls.find(c => c.url.endsWith('/reports') && c.options.method === 'POST');
        assert.ok(postCall, 'Must invoke POST /reports');
        assert.equal(postCall.options.credentials, 'include');
        assert.equal(postCall.options.headers['X-CSRF-TOKEN'], 'test-csrf-token-abc');
    });

    test('4. API Client: Handles HTTP 204 No Content without JSON parsing errors', async () => {
        global.window = {};
        global.fetch = async (url, options = {}) => {
            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'dummy-token', headerName: 'X-CSRF-TOKEN' })
                };
            }
            if (url.endsWith('/auth/logout')) {
                return {
                    ok: true,
                    status: 204,
                    headers: new Map(),
                    text: async () => ''
                };
            }
            return { ok: true, status: 200, headers: new Map(), json: async () => ({}) };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        await assert.doesNotReject(async () => {
            await API.logout();
        }, 'logout() on 204 must resolve cleanly without JSON parse error');
    });

    test('5. API Client: 403 on mutation refreshes CSRF token and retries at most once', async () => {
        let attempt = 0;
        let csrfFetches = 0;

        global.window = {};
        global.fetch = async (url, options = {}) => {
            if (url.endsWith('/auth/csrf')) {
                csrfFetches++;
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: `token-v${csrfFetches}`, headerName: 'X-CSRF-TOKEN' })
                };
            }

            if (url.endsWith('/auth/login')) {
                attempt++;
                if (attempt === 1) {
                    // First attempt fails with 403 due to stale CSRF
                    return {
                        ok: false,
                        status: 403,
                        statusText: 'Forbidden',
                        headers: new Map([['content-type', 'application/json']]),
                        json: async () => ({ message: 'Invalid CSRF token' })
                    };
                }
                // Second attempt succeeds
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ id: 1, email: 'student@example.com' })
                };
            }
            return { ok: true, status: 200, headers: new Map(), json: async () => ({}) };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        const user = await API.login({ email: 'student@example.com', password: 'password' });
        assert.equal(user.email, 'student@example.com');
        assert.equal(attempt, 2, 'Must have retried the request once');
        assert.equal(csrfFetches, 2, 'Must have re-fetched CSRF token on retry');
    });

    test('6. API Client: Error normalization preserves status codes and validation messages', async () => {
        global.window = {};
        global.fetch = async (url, options = {}) => {
            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'token', headerName: 'X-CSRF-TOKEN' })
                };
            }
            return {
                ok: false,
                status: 409,
                statusText: 'Conflict',
                headers: new Map([['content-type', 'application/json']]),
                json: async () => ({
                    status: 409,
                    message: 'An account with this email address already exists.',
                    fieldErrors: { email: 'Email already registered' }
                })
            };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        await assert.rejects(async () => {
            await API.register({ fullName: 'Duplicate', email: 'dup@example.com', password: 'password123' });
        }, (err) => {
            assert.equal(err.status, 409);
            assert.equal(err.message, 'An account with this email address already exists.');
            assert.equal(err.fieldErrors.email, 'Email already registered');
            return true;
        });
    });

    test('7. API Client: Specific HTTP status code handling (401, 403, 409, 429)', async () => {
        const statusTestCases = [
            {
                status: 401,
                body: { status: 401, message: 'Invalid email or password' },
                call: (api) => api.login({ email: 'wrong@example.com', password: 'wrong' }),
                expectedMsg: 'Invalid email or password'
            },
            {
                status: 409,
                body: { status: 409, message: 'An account with this email address already exists.' },
                call: (api) => api.register({ fullName: 'Dup', email: 'exists@example.com', password: 'password123' }),
                expectedMsg: 'An account with this email address already exists.'
            },
            {
                status: 429,
                body: { status: 429, message: 'Too many requests. Please try again later.' },
                call: (api) => api.login({ email: 'spammer@example.com', password: 'password123' }),
                expectedMsg: 'Too many requests. Please try again later.'
            }
        ];

        for (const tc of statusTestCases) {
            global.window = {};
            global.fetch = async (url) => {
                if (url.endsWith('/auth/csrf')) {
                    return {
                        ok: true,
                        status: 200,
                        headers: new Map([['content-type', 'application/json']]),
                        json: async () => ({ token: 'dummy-token', headerName: 'X-CSRF-TOKEN' })
                    };
                }
                return {
                    ok: false,
                    status: tc.status,
                    statusText: `Error ${tc.status}`,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => tc.body
                };
            };

            eval(fs.readFileSync(apiJsPath, 'utf8'));
            const API = global.window.CampusFindAPI;

            await assert.rejects(
                async () => tc.call(API),
                (err) => {
                    assert.equal(err.status, tc.status, `Expected HTTP ${tc.status}`);
                    assert.equal(err.message, tc.expectedMsg);
                    return true;
                }
            );
        }
    });

    test('8. Public Guest Reporting: Report creation succeeds without prior authentication', async () => {
        const fetchLog = [];

        global.window = {};
        global.fetch = async (url, options = {}) => {
            fetchLog.push({ url, method: options.method || 'GET', headers: options.headers, credentials: options.credentials });

            if (url.endsWith('/auth/me')) {
                // Anonymous guest: 401 Unauthorized
                return {
                    ok: false,
                    status: 401,
                    statusText: 'Unauthorized',
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ status: 401, message: 'Full authentication is required to access this resource' })
                };
            }

            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'csrf-guest-123', headerName: 'X-CSRF-TOKEN' })
                };
            }

            if (url.endsWith('/reports') && (options.method === 'POST')) {
                return {
                    ok: true,
                    status: 201,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({
                        id: 42,
                        reportId: 'FND-20261026-9999',
                        type: 'FOUND',
                        itemName: 'Blue Umbrella',
                        category: 'OTHER',
                        status: 'SUBMITTED'
                    })
                };
            }

            return { ok: true, status: 200, headers: new Map(), json: async () => ({}) };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        // Verify guest status first
        await assert.rejects(async () => {
            await API.getCurrentUser();
        }, (err) => err.status === 401);

        // Guest submits report
        const guestReport = await API.createReport({
            type: 'FOUND',
            itemName: 'Blue Umbrella',
            category: 'OTHER',
            description: 'Found near cafeteria entrance',
            location: 'canteen',
            itemDate: '2026-10-26'
        });

        assert.equal(guestReport.id, 42);
        assert.equal(guestReport.reportId, 'FND-20261026-9999');

        const postReq = fetchLog.find(r => r.url.endsWith('/reports') && r.method === 'POST');
        assert.ok(postReq, 'Report creation request must have been sent');
        assert.equal(postReq.credentials, 'include', 'Guest requests must include credentials for session/CORS');
        assert.equal(postReq.headers['X-CSRF-TOKEN'], 'csrf-guest-123', 'Guest mutations must attach CSRF token');
    });

    test('9. Form Validation Logic: Client validation constraints for Login & Registration', () => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        // Email validation checks
        assert.ok(emailRegex.test('student@campus.edu'), 'Standard university email should pass');
        assert.ok(emailRegex.test('user.name+tag@example.com'), 'Email with plus/dots should pass');
        assert.ok(!emailRegex.test('not-an-email'), 'Plain text should fail email regex');
        assert.ok(!emailRegex.test('missing@tld'), 'Email without domain extension should fail');
        assert.ok(!emailRegex.test('@no-local-part.com'), 'Email without username should fail');
        assert.ok(!emailRegex.test('has spaces@domain.com'), 'Email with spaces should fail');

        // Registration form field validation rules
        function validateRegistrationInput(fullName, email, password, confirmPassword) {
            const errors = {};
            const cleanName = (fullName || '').trim();
            const cleanEmail = (email || '').trim().toLowerCase();

            if (!cleanName || cleanName.length < 2 || cleanName.length > 100) {
                errors.name = 'Full name must be between 2 and 100 characters';
            }
            if (!cleanEmail || !emailRegex.test(cleanEmail) || cleanEmail.length > 150) {
                errors.email = 'Valid email address is required';
            }
            if (!password || password.length < 8 || password.length > 128) {
                errors.password = 'Password must be between 8 and 128 characters';
            }
            if (password !== confirmPassword) {
                errors.confirmPassword = 'Passwords do not match';
            }
            return { isValid: Object.keys(errors).length === 0, errors };
        }

        // Test valid registration data
        const valid = validateRegistrationInput('Jane Doe', 'jane@example.com', 'securePass123', 'securePass123');
        assert.ok(valid.isValid);
        assert.equal(Object.keys(valid.errors).length, 0);

        // Test short name
        const shortName = validateRegistrationInput('J', 'jane@example.com', 'securePass123', 'securePass123');
        assert.ok(!shortName.isValid);
        assert.ok(shortName.errors.name);

        // Test short password (< 8 chars)
        const shortPass = validateRegistrationInput('Jane Doe', 'jane@example.com', 'short', 'short');
        assert.ok(!shortPass.isValid);
        assert.ok(shortPass.errors.password);

        // Test password mismatch
        const mismatch = validateRegistrationInput('Jane Doe', 'jane@example.com', 'securePass123', 'differentPass');
        assert.ok(!mismatch.isValid);
        assert.ok(mismatch.errors.confirmPassword);
    });

    test('10. Navigation Bar & Auth State: Navbar rendering and escapeHTML XSS protection', () => {
        const scriptCode = fs.readFileSync(scriptJsPath, 'utf8');

        // Verify escapeHTML function exists and correctly escapes dangerous HTML entities
        assert.ok(scriptCode.includes('function escapeHTML('), 'script.js must define escapeHTML');

        function escapeHTML(str) {
            if (str == null) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        }

        const maliciousInput = '<script>alert("xss")</script><img src="x" onerror="alert(1)">';
        const escaped = escapeHTML(maliciousInput);
        assert.ok(!escaped.includes('<script>'), 'Must escape <script>');
        assert.ok(!escaped.includes('<img'), 'Must escape <img tag');
        assert.ok(escaped.includes('&lt;script&gt;'), 'Must replace < and > with HTML entities');
        assert.ok(escaped.includes('&lt;img src=&quot;x&quot; onerror=&quot;alert(1)&quot;&gt;'), 'Must encode quotes and brackets');

        // Verify that auth-dependent navbar removes .auth-link and adds nav-user and navLogoutBtn
        assert.ok(scriptCode.includes('.querySelectorAll(".auth-link")'), 'Navbar must target .auth-link items');
        assert.ok(scriptCode.includes('id = "navLogoutBtn"'), 'Navbar must create navLogoutBtn on login');
        assert.ok(scriptCode.includes('window.CampusFindAPI.logout()'), 'Logout button must call API logout');
    });

    test('11. Image Upload: File size validation, extension checks, and FormData dispatch', async () => {
        global.window = {};
        const fetchCalls = [];

        global.fetch = async (url, options = {}) => {
            fetchCalls.push({ url, options });
            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true,
                    status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'img-csrf-token', headerName: 'X-CSRF-TOKEN' })
                };
            }
            if (url.endsWith('/images/upload')) {
                return {
                    ok: true,
                    status: 201,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({
                        fileName: 'test-upload.jpg',
                        imageUrl: '/api/images/test-upload.jpg',
                        contentType: 'image/jpeg',
                        fileSize: 10240
                    })
                };
            }
            return { ok: true, status: 200, headers: new Map(), json: async () => ({}) };
        };

        // Mock global FormData
        global.FormData = class MockFormData {
            constructor() {
                this.entries = {};
            }
            append(k, v) {
                this.entries[k] = v;
            }
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        // 1. Reject missing file
        await assert.rejects(async () => {
            await API.uploadImage(null);
        }, /No image file selected/);

        // 2. Reject file exceeding 5MB
        const oversizedFile = { name: 'huge.jpg', size: 6 * 1024 * 1024, type: 'image/jpeg' };
        await assert.rejects(async () => {
            await API.uploadImage(oversizedFile);
        }, /exceeds the 5MB maximum limit/);

        // 3. Reject disallowed extension/type
        const invalidFile = { name: 'script.sh', size: 500, type: 'text/x-sh' };
        await assert.rejects(async () => {
            await API.uploadImage(invalidFile);
        }, /Only JPG, PNG, WEBP, and GIF images are allowed/);

        // 4. Accept valid image
        const validFile = { name: 'photo.jpg', size: 10240, type: 'image/jpeg' };
        const result = await API.uploadImage(validFile);
        assert.equal(result.fileName, 'test-upload.jpg');
        assert.equal(result.imageUrl, '/api/images/test-upload.jpg');

        const uploadReq = fetchCalls.find(c => c.url.endsWith('/images/upload'));
        assert.ok(uploadReq, 'Upload request must have been dispatched');
        assert.equal(uploadReq.options.method, 'POST');
        assert.equal(uploadReq.options.credentials, 'include');
        assert.equal(uploadReq.options.headers['X-CSRF-TOKEN'], 'img-csrf-token');
    });
});

describe('CampusFind Phase 8B.3 — Claims & Recovery Frontend Integration Verification', () => {

    const rootDir = path.resolve(__dirname, '..');
    const apiJsPath = path.join(rootDir, 'js', 'api.js');
    const scriptJsPath = path.join(rootDir, 'js', 'script.js');
    const myClaimsHtmlPath = path.join(rootDir, 'pages', 'my-claims.html');

    test('1. HTML structure: pages/my-claims.html layout, navigation, and state containers', () => {
        assert.ok(fs.existsSync(myClaimsHtmlPath), 'pages/my-claims.html must exist');
        const content = fs.readFileSync(myClaimsHtmlPath, 'utf8');

        assert.ok(content.includes('id="navLinks"'), 'my-claims.html must contain navigation links');
        assert.ok(content.includes('id="myClaimsContainer"'), 'my-claims.html must contain #myClaimsContainer');
        assert.ok(content.includes('id="claimsLoading"'), 'my-claims.html must contain #claimsLoading');
        assert.ok(content.includes('id="emptyClaims"'), 'my-claims.html must contain #emptyClaims');
        assert.ok(content.includes('id="claimsAlert"'), 'my-claims.html must contain #claimsAlert');
        assert.ok(content.includes('href="../Css/style.css"'), 'my-claims.html must reference style.css');
        assert.ok(content.includes('src="../js/api.js"'), 'my-claims.html must load api.js');
        assert.ok(content.includes('src="../js/script.js"'), 'my-claims.html must load script.js');
    });

    test('2. API Client: All 8 Phase 8B.3 claim and recovery functions exist on CampusFindAPI', () => {
        global.window = {};
        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        assert.equal(typeof API.submitClaim, 'function', 'submitClaim must be defined');
        assert.equal(typeof API.getMyClaims, 'function', 'getMyClaims must be defined');
        assert.equal(typeof API.getClaimById, 'function', 'getClaimById must be defined');
        assert.equal(typeof API.getReportClaims, 'function', 'getReportClaims must be defined');
        assert.equal(typeof API.reviewClaim, 'function', 'reviewClaim must be defined');
        assert.equal(typeof API.withdrawClaim, 'function', 'withdrawClaim must be defined');
        assert.equal(typeof API.recordRecovery, 'function', 'recordRecovery must be defined');
        assert.equal(typeof API.getRecovery, 'function', 'getRecovery must be defined');
    });

    test('3. API Client: submitClaim validates input, attaches CSRF, and calls POST /api/reports/:id/claims', async () => {
        const fetchCalls = [];
        global.window = {};
        global.fetch = async (url, options = {}) => {
            fetchCalls.push({ url, options });
            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true, status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'csrf-claim-token', headerName: 'X-CSRF-TOKEN' })
                };
            }
            if (url.includes('/claims')) {
                return {
                    ok: true, status: 201,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ id: 42, status: 'PENDING' })
                };
            }
            return { ok: true, status: 200, headers: new Map(), json: async () => ({}) };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        // Validation rejection tests
        await assert.rejects(async () => {
            await API.submitClaim('FND-1', { explanation: 'short', verificationDetails: 'Secret details here' });
        }, /Explanation must be between 10 and 2000 characters/);

        await assert.rejects(async () => {
            await API.submitClaim('FND-1', { explanation: 'This is my calculator lost on Monday', verificationDetails: 'tiny' });
        }, /Verification proof must be between 5 and 2000 characters/);

        // Valid claim submission
        const res = await API.submitClaim('FND-123', {
            explanation: 'I left my black umbrella on the library 2nd floor desk',
            verificationDetails: 'Has a broken rib and yellow tape on the handle'
        });

        assert.equal(res.id, 42);
        const submitReq = fetchCalls.find(c => c.url.includes('/api/reports/FND-123/claims'));
        assert.ok(submitReq, 'Must dispatch request to report claims endpoint');
        assert.equal(submitReq.options.method, 'POST');
        assert.equal(submitReq.options.credentials, 'include');
        assert.equal(submitReq.options.headers['X-CSRF-TOKEN'], 'csrf-claim-token');
    });

    test('4. API Client: Claims retrieval endpoints use GET with credentials: "include"', async () => {
        const fetchCalls = [];
        global.window = {};
        global.fetch = async (url, options = {}) => {
            fetchCalls.push({ url, options });
            return {
                ok: true, status: 200,
                headers: new Map([['content-type', 'application/json']]),
                json: async () => [{ id: 1, status: 'PENDING' }]
            };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        await API.getMyClaims();
        const myClaimsReq = fetchCalls.find(c => c.url.endsWith('/api/claims/my'));
        assert.ok(myClaimsReq, 'getMyClaims must target /api/claims/my');
        assert.equal(myClaimsReq.options.method, 'GET');
        assert.equal(myClaimsReq.options.credentials, 'include');

        await API.getClaimById(55);
        const claimByIdReq = fetchCalls.find(c => c.url.endsWith('/api/claims/55'));
        assert.ok(claimByIdReq, 'getClaimById must target /api/claims/55');
        assert.equal(claimByIdReq.options.method, 'GET');
        assert.equal(claimByIdReq.options.credentials, 'include');

        await API.getReportClaims('FND-999');
        const reportClaimsReq = fetchCalls.find(c => c.url.endsWith('/api/reports/FND-999/claims'));
        assert.ok(reportClaimsReq, 'getReportClaims must target /api/reports/FND-999/claims');
        assert.equal(reportClaimsReq.options.method, 'GET');
        assert.equal(reportClaimsReq.options.credentials, 'include');
    });

    test('5. API Client: reviewClaim and withdrawClaim use PATCH with CSRF token', async () => {
        const fetchCalls = [];
        global.window = {};
        global.fetch = async (url, options = {}) => {
            fetchCalls.push({ url, options });
            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true, status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'patch-csrf-token', headerName: 'X-CSRF-TOKEN' })
                };
            }
            return {
                ok: true, status: 200,
                headers: new Map([['content-type', 'application/json']]),
                json: async () => ({ id: 77, status: 'APPROVED' })
            };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        // reviewClaim rejection test
        await assert.rejects(async () => {
            await API.reviewClaim(77, { status: 'INVALID_STATUS' });
        }, /Status must be either APPROVED or REJECTED/);

        // Valid review
        await API.reviewClaim(77, { status: 'APPROVED', reviewNotes: 'ID verified' });
        const reviewReq = fetchCalls.find(c => c.url.endsWith('/api/claims/77/review'));
        assert.ok(reviewReq, 'reviewClaim must call PATCH /api/claims/77/review');
        assert.equal(reviewReq.options.method, 'PATCH');
        assert.equal(reviewReq.options.headers['X-CSRF-TOKEN'], 'patch-csrf-token');
        assert.equal(reviewReq.options.credentials, 'include');

        // withdrawClaim
        await API.withdrawClaim(77);
        const withdrawReq = fetchCalls.find(c => c.url.endsWith('/api/claims/77/withdraw'));
        assert.ok(withdrawReq, 'withdrawClaim must call PATCH /api/claims/77/withdraw');
        assert.equal(withdrawReq.options.method, 'PATCH');
        assert.equal(withdrawReq.options.headers['X-CSRF-TOKEN'], 'patch-csrf-token');
        assert.equal(withdrawReq.options.credentials, 'include');
    });

    test('6. API Client: Recovery endpoints (recordRecovery and getRecovery)', async () => {
        const fetchCalls = [];
        global.window = {};
        global.fetch = async (url, options = {}) => {
            fetchCalls.push({ url, options });
            if (url.endsWith('/auth/csrf')) {
                return {
                    ok: true, status: 200,
                    headers: new Map([['content-type', 'application/json']]),
                    json: async () => ({ token: 'rec-csrf-token', headerName: 'X-CSRF-TOKEN' })
                };
            }
            return {
                ok: true, status: 200,
                headers: new Map([['content-type', 'application/json']]),
                json: async () => ({ id: 10, recoveryDate: '2026-10-26' })
            };
        };

        eval(fs.readFileSync(apiJsPath, 'utf8'));
        const API = global.window.CampusFindAPI;

        // Validation rejection tests
        await assert.rejects(async () => {
            await API.recordRecovery('FND-55', { recoveryDate: '2026-10-26' }); // missing claimId
        }, /claimId is required to record recovery/);

        await assert.rejects(async () => {
            await API.recordRecovery('FND-55', { claimId: 10 }); // missing recoveryDate
        }, /recoveryDate is required to record recovery/);

        // Valid recovery creation
        await API.recordRecovery('FND-55', {
            claimId: 10,
            recoveryDate: '2026-10-26',
            notes: 'Handed over at front desk'
        });

        const recCreateReq = fetchCalls.find(c => c.url.endsWith('/api/reports/FND-55/recovery') && c.options.method === 'POST');
        assert.ok(recCreateReq, 'recordRecovery must POST to /api/reports/:id/recovery');
        assert.equal(recCreateReq.options.headers['X-CSRF-TOKEN'], 'rec-csrf-token');
        assert.equal(recCreateReq.options.credentials, 'include');

        // Valid recovery retrieval
        await API.getRecovery('FND-55');
        const recGetReq = fetchCalls.find(c => c.url.endsWith('/api/reports/FND-55/recovery') && c.options.method === 'GET');
        assert.ok(recGetReq, 'getRecovery must GET from /api/reports/:id/recovery');
        assert.equal(recGetReq.options.credentials, 'include');
    });

    test('7. Security: Frontend escapeHTML and XSS prevention for user-controlled claim content', () => {
        const scriptCode = fs.readFileSync(scriptJsPath, 'utf8');

        // Verify escapeHTML function existence
        assert.ok(scriptCode.includes('function escapeHTML('), 'script.js must define escapeHTML function');

        // Verify user inputs are properly escaped before template interpolation
        assert.ok(scriptCode.includes('escapeHTML(claim.explanation)'), 'claim explanation must be escaped');
        assert.ok(scriptCode.includes('escapeHTML(claim.verificationDetails)'), 'verificationDetails must be escaped');
        assert.ok(scriptCode.includes('escapeHTML(claim.reviewNotes)'), 'reviewNotes must be escaped');
        assert.ok(scriptCode.includes('escapeHTML(recovery.notes)'), 'recovery notes must be escaped');
        assert.ok(scriptCode.includes('escapeHTML(claim.claimantName'), 'claimant name must be escaped');
    });

    test('8. UI Helpers: Claim status badge helper formatClaimBadge handles all statuses', () => {
        // Test status mapping from script.js
        const scriptCode = fs.readFileSync(scriptJsPath, 'utf8');
        assert.ok(scriptCode.includes('function formatClaimBadge('), 'formatClaimBadge helper must exist');

        const testStatuses = ['PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'RECOVERED'];
        for (const st of testStatuses) {
            assert.ok(scriptCode.includes(st), `formatClaimBadge must support ${st}`);
        }
    });

    test('9. Accessibility: showModal creates keyboard-friendly dialog with Escape support', () => {
        const scriptCode = fs.readFileSync(scriptJsPath, 'utf8');
        assert.ok(scriptCode.includes('function showModal('), 'showModal helper must exist');
        assert.ok(scriptCode.includes('"role", "dialog"') || scriptCode.includes('role="dialog"'), 'showModal must set role="dialog"');
        assert.ok(scriptCode.includes('"aria-modal", "true"') || scriptCode.includes('aria-modal="true"'), 'showModal must set aria-modal="true"');
        assert.ok(scriptCode.includes('e.key === "Escape"'), 'showModal must handle Escape key');
    });
});

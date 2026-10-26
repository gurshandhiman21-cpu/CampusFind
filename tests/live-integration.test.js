const { test, describe } = require('node:test');
const assert = require('node:assert/strict');

const BACKEND_URL = 'http://localhost:8080/api';
const FRONTEND_ORIGIN = 'http://localhost:5500';

class CookieJar {
    constructor() {
        this.cookies = new Map();
    }
    update(res) {
        const raw = res.headers.getSetCookie ? res.headers.getSetCookie() : [res.headers.get('set-cookie')].filter(Boolean);
        for (const str of raw) {
            if (!str) continue;
            const parts = str.split(';')[0].split('=');
            if (parts.length >= 2) {
                this.cookies.set(parts[0].trim(), parts.slice(1).join('=').trim());
            }
        }
    }
    getCookieHeader() {
        const list = [];
        for (const [k, v] of this.cookies.entries()) {
            list.push(`${k}=${v}`);
        }
        return list.join('; ');
    }
    get(name) {
        return this.cookies.get(name);
    }
    clear() {
        this.cookies.clear();
    }
}

describe('CampusFind Phase 8A.4 Live Backend & MySQL Integration Verification', () => {

    const guestJar = new CookieJar();
    const userJar = new CookieJar();

    let guestCsrfToken = null;
    let userCsrfToken = null;
    let createdGuestReportId = null;
    let createdAuthReportId = null;
    let uploadedImageFileName = null;

    const testUser = {
        fullName: 'Phase 8A Live Tester',
        email: `tester_${Date.now()}@campusfind.test`,
        password: 'Password123!'
    };

    async function req(url, options = {}, jar) {
        const headers = {
            'Origin': FRONTEND_ORIGIN,
            ...(options.headers || {})
        };
        if (jar && jar.getCookieHeader()) {
            headers['Cookie'] = jar.getCookieHeader();
        }
        const res = await fetch(url, { ...options, headers });
        if (jar) {
            jar.update(res);
        }
        return res;
    }

    test('1. Check /api/health and public reports endpoint', async () => {
        // Health check
        const healthRes = await req(`${BACKEND_URL}/health`);
        assert.equal(healthRes.status, 200, 'Health endpoint must return 200');
        const healthData = await healthRes.json();
        assert.equal(healthData.status, 'UP');
        assert.equal(healthData.application, 'CampusFind Backend');

        // Public reports list
        const reportsRes = await req(`${BACKEND_URL}/reports`);
        assert.equal(reportsRes.status, 200, 'Reports listing must return 200 without authentication');
        const reportsData = await reportsRes.json();
        assert.ok(Array.isArray(reportsData), 'Reports response must be an array');
    });

    test('2. CSRF Token Acquisition and XSRF-TOKEN cookie', async () => {
        const csrfRes = await req(`${BACKEND_URL}/auth/csrf`, {}, guestJar);
        assert.equal(csrfRes.status, 200, 'CSRF endpoint must return 200');
        const csrfData = await csrfRes.json();

        assert.ok(csrfData.token, 'Must return a non-empty CSRF token');
        assert.equal(csrfData.headerName, 'X-CSRF-TOKEN');
        guestCsrfToken = csrfData.token;

        assert.ok(guestJar.get('XSRF-TOKEN'), 'Response must issue XSRF-TOKEN cookie');
    });

    test('3. CSRF Enforcement: unsafe requests rejected without valid token', async () => {
        // Attempt POST without CSRF header
        const unauthRes = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                type: 'LOST',
                itemName: 'CSRF Attack Test Item',
                category: 'electronics',
                description: 'Should be blocked by CSRF filter',
                location: 'library',
                incidentDate: '2026-10-26'
            })
        }, guestJar);

        assert.equal(unauthRes.status, 403, 'POST /reports without CSRF token must return 403 Forbidden');

        // Attempt POST with forged CSRF token
        const forgedRes = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': 'forged-token-xyz-123'
            },
            body: JSON.stringify({
                type: 'LOST',
                itemName: 'Forged CSRF Test',
                category: 'electronics',
                description: 'Should be blocked by CSRF filter',
                location: 'library',
                incidentDate: '2026-10-26'
            })
        }, guestJar);

        assert.equal(forgedRes.status, 403, 'POST /reports with invalid CSRF token must return 403 Forbidden');
    });

    test('4. CORS Enforcement: approved origin accepted, null/unapproved rejected credentialed access', async () => {
        // Approved origin
        const approvedRes = await fetch(`${BACKEND_URL}/health`, {
            headers: { 'Origin': FRONTEND_ORIGIN }
        });
        const approvedAllowOrigin = approvedRes.headers.get('access-control-allow-origin');
        assert.equal(approvedAllowOrigin, FRONTEND_ORIGIN, 'Approved origin must be allowed');
        assert.equal(approvedRes.headers.get('access-control-allow-credentials'), 'true');

        // Unapproved / null origin
        const nullRes = await fetch(`${BACKEND_URL}/health`, {
            headers: { 'Origin': 'null' }
        });
        const nullAllowOrigin = nullRes.headers.get('access-control-allow-origin');
        assert.notEqual(nullAllowOrigin, 'null', 'Origin: null must not receive credentialed CORS');

        const maliciousRes = await fetch(`${BACKEND_URL}/health`, {
            headers: { 'Origin': 'https://evil-attacker.com' }
        });
        const maliciousAllow = maliciousRes.headers.get('access-control-allow-origin');
        assert.notEqual(maliciousAllow, 'https://evil-attacker.com', 'Unapproved origins must not be allowed');
    });

    test('5. Guest report submission without login', async () => {
        const guestPayload = {
            type: 'FOUND',
            itemName: 'Live Synthetic Blue Keychain',
            category: 'other',
            description: 'Synthetic test item for live verification',
            location: 'canteen',
            incidentDate: '2026-10-26'
        };

        const res = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': guestCsrfToken
            },
            body: JSON.stringify(guestPayload)
        }, guestJar);

        assert.equal(res.status, 201, 'Guest report creation must return 201 Created');
        const data = await res.json();
        assert.ok(data.reportId, 'Must return a generated reportId');
        assert.equal(data.itemName, guestPayload.itemName);
        assert.equal(data.type, 'FOUND');
        assert.equal(data.status, 'OPEN');

        createdGuestReportId = data.reportId;
    });

    test('6. User Registration & Duplicate Email Rejection', async () => {
        // Acquire fresh CSRF for userJar
        const csrfRes = await req(`${BACKEND_URL}/auth/csrf`, {}, userJar);
        userCsrfToken = (await csrfRes.json()).token;

        // Successful registration
        const regRes = await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrfToken
            },
            body: JSON.stringify(testUser)
        }, userJar);

        assert.equal(regRes.status, 201, 'Registration must return 201 Created');
        const regData = await regRes.json();
        assert.ok(regData.id, 'Must return generated user ID');
        assert.equal(regData.email, testUser.email.toLowerCase());
        assert.equal(regData.fullName, testUser.fullName);
        assert.equal(regData.role, 'STUDENT');

        // Duplicate registration
        const dupRes = await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrfToken
            },
            body: JSON.stringify(testUser)
        }, userJar);

        assert.equal(dupRes.status, 409, 'Duplicate registration must return 409 Conflict');
        const dupData = await dupRes.json();
        assert.ok(dupData.message.includes('already exists'), 'Conflict message must indicate existing email');
    });

    test('7. Login, Session Persistence, and /api/auth/me', async () => {
        // Login with credentials
        const loginRes = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrfToken
            },
            body: JSON.stringify({
                email: testUser.email,
                password: testUser.password
            })
        }, userJar);

        assert.equal(loginRes.status, 200, 'Login must return 200 OK');
        const loginData = await loginRes.json();
        assert.equal(loginData.email, testUser.email.toLowerCase());

        assert.ok(userJar.get('JSESSIONID'), 'Login response must set JSESSIONID cookie');

        // Access /api/auth/me WITH session cookie
        const meRes = await req(`${BACKEND_URL}/auth/me`, {}, userJar);

        assert.equal(meRes.status, 200, '/api/auth/me must return 200 for authenticated session');
        const meData = await meRes.json();
        assert.equal(meData.email, testUser.email.toLowerCase());
        assert.equal(meData.role, 'STUDENT');

        // Access /api/auth/me WITHOUT session cookie (anonymous)
        const anonRes = await fetch(`${BACKEND_URL}/auth/me`, {
            headers: { 'Origin': FRONTEND_ORIGIN }
        });
        assert.equal(anonRes.status, 401, '/api/auth/me must return 401 Unauthorized for anonymous requests');
    });

    test('8. Authenticated report submission with user association', async () => {
        // Re-acquire fresh CSRF token with session cookie
        const csrfRes = await req(`${BACKEND_URL}/auth/csrf`, {}, userJar);
        userCsrfToken = (await csrfRes.json()).token;

        const authPayload = {
            type: 'LOST',
            itemName: 'Live Synthetic Student Calculator',
            category: 'electronics',
            description: 'Submitted while authenticated by live tester',
            location: 'block-a',
            incidentDate: '2026-10-26'
        };

        const res = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrfToken
            },
            body: JSON.stringify(authPayload)
        }, userJar);

        assert.equal(res.status, 201, 'Authenticated report creation must return 201 Created');
        const data = await res.json();
        assert.ok(data.reportId, 'Must return generated reportId');
        assert.equal(data.itemName, authPayload.itemName);
        assert.equal(data.type, 'LOST');

        createdAuthReportId = data.reportId;
    });

    test('9. Image upload and image retrieval', async () => {
        // Create a 1x1 transparent PNG binary
        const pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
        const pngBuffer = Buffer.from(pngBase64, 'base64');
        const blob = new Blob([pngBuffer], { type: 'image/png' });

        const formData = new FormData();
        formData.append('file', blob, 'synthetic_test.png');

        // Re-fetch CSRF token with userJar
        const csrfRes = await req(`${BACKEND_URL}/auth/csrf`, {}, userJar);
        userCsrfToken = (await csrfRes.json()).token;

        const uploadRes = await req(`${BACKEND_URL}/images/upload`, {
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': userCsrfToken
            },
            body: formData
        }, userJar);

        assert.equal(uploadRes.status, 201, 'Image upload must return 201 Created');
        const uploadData = await uploadRes.json();
        assert.ok(uploadData.fileName, 'Must return fileName');
        assert.ok(uploadData.imageUrl, 'Must return imageUrl');
        uploadedImageFileName = uploadData.fileName;

        // Retrieve uploaded image
        const imgFetchRes = await fetch(`http://localhost:8080${uploadData.imageUrl}`);
        assert.equal(imgFetchRes.status, 200, 'Retrieving uploaded image must return 200 OK');
        assert.equal(imgFetchRes.headers.get('content-type'), 'image/png');
        const arrayBuf = await imgFetchRes.arrayBuffer();
        assert.equal(arrayBuf.byteLength, pngBuffer.length, 'Retrieved image size must match uploaded image size');
    });

    test('10. MySQL Persistence & Retrieval: retrieve created reports by reportId', async () => {
        // Retrieve Guest Report
        const guestRes = await req(`${BACKEND_URL}/reports/${createdGuestReportId}`);
        assert.equal(guestRes.status, 200, `Guest report ${createdGuestReportId} must be retrievable from MySQL`);
        const guestData = await guestRes.json();
        assert.equal(guestData.reportId, createdGuestReportId);
        assert.equal(guestData.itemName, 'Live Synthetic Blue Keychain');

        // Retrieve Authenticated Report
        const authRes = await req(`${BACKEND_URL}/reports/${createdAuthReportId}`);
        assert.equal(authRes.status, 200, `Auth report ${createdAuthReportId} must be retrievable from MySQL`);
        const authData = await authRes.json();
        assert.equal(authData.reportId, createdAuthReportId);
        assert.equal(authData.itemName, 'Live Synthetic Student Calculator');
    });

    test('11. User Logout: Invalidates session and clears session cookie', async () => {
        // Re-acquire fresh CSRF token
        const csrfRes = await req(`${BACKEND_URL}/auth/csrf`, {}, userJar);
        userCsrfToken = (await csrfRes.json()).token;

        const logoutRes = await req(`${BACKEND_URL}/auth/logout`, {
            method: 'POST',
            headers: {
                'X-CSRF-TOKEN': userCsrfToken
            }
        }, userJar);

        assert.equal(logoutRes.status, 204, 'Logout must return 204 No Content');

        // Verify session is now invalidated
        const postLogoutMe = await req(`${BACKEND_URL}/auth/me`, {}, userJar);

        assert.equal(postLogoutMe.status, 401, 'Accessing /api/auth/me after logout must return 401 Unauthorized');
    });
});

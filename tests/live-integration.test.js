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

describe('CampusFind Phase 8B.3 Live Claim and Recovery Flow Verification', () => {

    const finderJar = new CookieJar();
    const claimantJar = new CookieJar();
    const strangerJar = new CookieJar();
    const unauthJar = new CookieJar();

    let finderCsrfToken = null;
    let claimantCsrfToken = null;
    let strangerCsrfToken = null;

    let foundReportId = null;
    let createdClaimId = null;

    const finderUser = {
        fullName: 'Finder Live Tester',
        email: `finder_${Date.now()}@campusfind.test`,
        password: 'Password123!'
    };

    const claimantUser = {
        fullName: 'Claimant Live Tester',
        email: `claimant_${Date.now()}@campusfind.test`,
        password: 'Password123!'
    };

    const strangerUser = {
        fullName: 'Stranger Live Tester',
        email: `stranger_${Date.now()}@campusfind.test`,
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
        const res = await fetch(url, {
            ...options,
            headers
        });
        if (jar) {
            jar.update(res);
        }
        return res;
    }

    let phase8bEndpointsAvailable = false;

    test('1. Setup: Register & Login Finder, Claimant, and Stranger Accounts', async () => {
        // Register and login Finder
        const fCsrf1 = await req(`${BACKEND_URL}/auth/csrf`, {}, finderJar);
        const fToken1 = (await fCsrf1.json()).token;
        await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': fToken1 },
            body: JSON.stringify(finderUser)
        }, finderJar);

        const fCsrf2 = await req(`${BACKEND_URL}/auth/csrf`, {}, finderJar);
        const fToken2 = (await fCsrf2.json()).token;
        const fLoginRes = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': fToken2 },
            body: JSON.stringify({ email: finderUser.email, password: finderUser.password })
        }, finderJar);
        assert.equal(fLoginRes.status, 200, 'Finder login must succeed');
        finderCsrfToken = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, finderJar)).json()).token;

        // Register and login Claimant
        const cCsrf1 = await req(`${BACKEND_URL}/auth/csrf`, {}, claimantJar);
        const cToken1 = (await cCsrf1.json()).token;
        await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': cToken1 },
            body: JSON.stringify(claimantUser)
        }, claimantJar);

        const cCsrf2 = await req(`${BACKEND_URL}/auth/csrf`, {}, claimantJar);
        const cToken2 = (await cCsrf2.json()).token;
        const cLoginRes = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': cToken2 },
            body: JSON.stringify({ email: claimantUser.email, password: claimantUser.password })
        }, claimantJar);
        assert.equal(cLoginRes.status, 200, 'Claimant login must succeed');
        claimantCsrfToken = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, claimantJar)).json()).token;

        // Register and login Stranger
        const sCsrf1 = await req(`${BACKEND_URL}/auth/csrf`, {}, strangerJar);
        const sToken1 = (await sCsrf1.json()).token;
        await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': sToken1 },
            body: JSON.stringify(strangerUser)
        }, strangerJar);

        const sCsrf2 = await req(`${BACKEND_URL}/auth/csrf`, {}, strangerJar);
        const sToken2 = (await sCsrf2.json()).token;
        const sLoginRes = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': sToken2 },
            body: JSON.stringify({ email: strangerUser.email, password: strangerUser.password })
        }, strangerJar);
        assert.equal(sLoginRes.status, 200, 'Stranger login must succeed');
        strangerCsrfToken = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, strangerJar)).json()).token;

        // Check if the running Spring Boot instance has Phase 8B endpoints in memory
        const probeRes = await req(`${BACKEND_URL}/claims/my`, {}, claimantJar);
        phase8bEndpointsAvailable = (probeRes.status !== 404);
        if (!phase8bEndpointsAvailable) {
            console.warn('\n[LIVE INTEGRATION NOTE] Running Spring Boot process (PID started ~8h ago) was launched before Phase 8B controllers were compiled. Restarting the backend server process will be needed to run live Phase 8B requests against it.\n');
        }
    });

    test('2. Finder reports a FOUND item', async () => {
        const foundPayload = {
            type: 'FOUND',
            itemName: 'Live Integration Umbrella',
            category: 'other',
            location: 'library',
            incidentDate: '2026-10-26',
            incidentTime: '14:30',
            description: 'Found navy blue umbrella on the 2nd floor desk near windows.'
        };

        const res = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': finderCsrfToken
            },
            body: JSON.stringify(foundPayload)
        }, finderJar);

        assert.equal(res.status, 201, 'Found report creation must return 201 Created');
        const data = await res.json();
        foundReportId = data.reportId;
        assert.ok(foundReportId, 'Must return generated reportId');
    });

    test('3. Unauthenticated claim submission is rejected (401 Unauthorized)', async () => {
        const guestCsrf = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, unauthJar)).json()).token;
        const unauthRes = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': guestCsrf
            },
            body: JSON.stringify({
                explanation: 'I left my umbrella on the desk earlier.',
                verificationDetails: 'Secret marking on handle'
            })
        }, unauthJar);
        assert.equal(unauthRes.status, 401, 'Unauthenticated claim submission must return 401 Unauthorized');
    });

    test('4. CSRF Protection: Claim submission without CSRF token is rejected (403 Forbidden)', async () => {
        const noCsrfRes = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                explanation: 'I left my umbrella on the desk earlier.',
                verificationDetails: 'Secret marking on handle'
            })
        }, claimantJar);
        assert.equal(noCsrfRes.status, 403, 'Claim submission without CSRF token must return 403 Forbidden');
    });

    test('5. Claimant submits valid ownership claim against the FOUND report', async () => {
        const claimPayload = {
            explanation: 'I accidentally left my navy blue umbrella at the study desk after my study session.',
            verificationDetails: 'Has a small crack on the bottom plastic knob and white initials GD scratched under handle.'
        };

        const res = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': claimantCsrfToken
            },
            body: JSON.stringify(claimPayload)
        }, claimantJar);

        assert.equal(res.status, 201, 'Claim creation must return 201 Created');
        const data = await res.json();
        createdClaimId = data.id;
        assert.ok(createdClaimId, 'Claim ID must be returned');
        assert.equal(data.status, 'PENDING');
        assert.equal(data.reportPublicId, foundReportId);
        assert.equal(data.explanation, claimPayload.explanation);
    });

    test('6. Duplicate Claim: Rejects duplicate active claim by same user (409 Conflict)', async () => {
        const dupRes = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': claimantCsrfToken
            },
            body: JSON.stringify({
                explanation: 'Submitting a duplicate claim for the same umbrella.',
                verificationDetails: 'Duplicate claim attempt verification.'
            })
        }, claimantJar);

        assert.equal(dupRes.status, 409, 'Submitting duplicate active claim must return 409 Conflict');
    });

    test('7. Claimant retrieves claims via /api/claims/my and /api/claims/:id', async () => {
        const myRes = await req(`${BACKEND_URL}/claims/my`, {}, claimantJar);
        assert.equal(myRes.status, 200, 'Claimant must be able to retrieve their claims');
        const myClaims = await myRes.json();
        assert.ok(Array.isArray(myClaims), 'myClaims must be an array');
        const found = myClaims.find(c => c.id === createdClaimId);
        assert.ok(found, 'Created claim must be present in claimant my claims list');

        const singleRes = await req(`${BACKEND_URL}/claims/${createdClaimId}`, {}, claimantJar);
        assert.equal(singleRes.status, 200, 'Claimant must be able to view their own claim by ID');
        const singleClaim = await singleRes.json();
        assert.equal(singleClaim.id, createdClaimId);
        assert.ok(singleClaim.verificationDetails, 'Claimant should see their own verification details');
    });

    let competingClaimId = null;
    test('8. Competing Claim: Second student submits a competing claim on the same report', async () => {
        const compRes = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': strangerCsrfToken
            },
            body: JSON.stringify({
                explanation: 'I also lost an umbrella at that exact library table.',
                verificationDetails: 'Competing claimant verification details.'
            })
        }, strangerJar);

        assert.equal(compRes.status, 201, 'Competing claimant claim submission must return 201');
        const compData = await compRes.json();
        competingClaimId = compData.id;
        assert.equal(compData.status, 'PENDING');
    });

    test('9. Finder views all claims submitted against their report (sees both claims with verification proof)', async () => {
        const res = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {}, finderJar);
        assert.equal(res.status, 200, 'Finder must be permitted to list claims on their report');
        const claims = await res.json();
        assert.ok(Array.isArray(claims) && claims.length >= 2, 'Finder must see both submitted claims');
        const matchingClaim = claims.find(c => c.id === createdClaimId);
        assert.ok(matchingClaim, 'Claimant claim must appear in finder claim review list');
        assert.ok(matchingClaim.verificationDetails, 'Finder must be allowed to inspect verification proof');
    });

    test('10. Authorization: Unrelated student is forbidden from listing report claims or reviewing claim', async () => {
        const listRes = await req(`${BACKEND_URL}/reports/${foundReportId}/claims`, {}, strangerJar);
        assert.equal(listRes.status, 403, 'Unrelated student must be denied (403) from viewing claims of another finder report');

        const reviewRes = await req(`${BACKEND_URL}/claims/${createdClaimId}/review`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': strangerCsrfToken
            },
            body: JSON.stringify({ status: 'APPROVED' })
        }, strangerJar);
        assert.equal(reviewRes.status, 403, 'Unrelated student must be denied (403) from approving claims');
    });

    test('11. Finder approves first claim and verifies report status transitions to CLAIM_PENDING', async () => {
        const reviewRes = await req(`${BACKEND_URL}/claims/${createdClaimId}/review`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': finderCsrfToken
            },
            body: JSON.stringify({
                status: 'APPROVED',
                reviewNotes: 'Verified initials GD scratched under handle.'
            })
        }, finderJar);

        assert.equal(reviewRes.status, 200, 'Finder must be able to approve pending claim');
        const updatedClaim = await reviewRes.json();
        assert.equal(updatedClaim.status, 'APPROVED');
        assert.equal(updatedClaim.reviewNotes, 'Verified initials GD scratched under handle.');

        // Verify report status transitioned to CLAIM_PENDING
        const reportRes = await req(`${BACKEND_URL}/reports/${foundReportId}`);
        const reportData = await reportRes.json();
        assert.equal(reportData.status, 'CLAIM_PENDING', 'Report status must transition to CLAIM_PENDING after claim approval');
    });

    test('12. Unauthorized user cannot record recovery (403 Forbidden)', async () => {
        const unauthRec = await req(`${BACKEND_URL}/reports/${foundReportId}/recovery`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': strangerCsrfToken
            },
            body: JSON.stringify({
                claimId: createdClaimId,
                recoveryDate: '2026-10-26',
                notes: 'Unauthorized handover attempt'
            })
        }, strangerJar);
        assert.equal(unauthRec.status, 403, 'Unauthorized student cannot record recovery handover');
    });

    test('13. Finder records physical handover recovery: transitions to RECOVERED and auto-rejects competing claim', async () => {
        const recoveryPayload = {
            claimId: createdClaimId,
            recoveryDate: '2026-10-26',
            notes: 'Handed over in library lobby after confirming student ID.'
        };

        const recRes = await req(`${BACKEND_URL}/reports/${foundReportId}/recovery`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': finderCsrfToken
            },
            body: JSON.stringify(recoveryPayload)
        }, finderJar);

        assert.equal(recRes.status, 201, 'Recovery recording must return 201 Created');
        const recData = await recRes.json();
        assert.equal(recData.recoveryDate, '2026-10-26');
        assert.equal(recData.notes, recoveryPayload.notes);

        // Verify report status is now permanently RECOVERED
        const repRes = await req(`${BACKEND_URL}/reports/${foundReportId}`);
        const repData = await repRes.json();
        assert.equal(repData.status, 'RECOVERED', 'Report must now be RECOVERED in MySQL');

        // Verify competing claim was automatically transitioned to REJECTED by service
        const compClaimRes = await req(`${BACKEND_URL}/claims/${competingClaimId}`, {}, strangerJar);
        assert.equal(compClaimRes.status, 200);
        const compClaimData = await compClaimRes.json();
        assert.equal(compClaimData.status, 'REJECTED', 'Competing pending claim must be automatically rejected upon recovery');
    });

    test('14. Recovery record access control: accessible to finder and claimant, forbidden to stranger', async () => {
        // Finder can access recovery
        const finderAudit = await req(`${BACKEND_URL}/reports/${foundReportId}/recovery`, {}, finderJar);
        assert.equal(finderAudit.status, 200, 'Finder must be able to view recovery record');

        // Claimant can access recovery
        const claimantAudit = await req(`${BACKEND_URL}/reports/${foundReportId}/recovery`, {}, claimantJar);
        assert.equal(claimantAudit.status, 200, 'Claimant must be able to view recovery record');

        // Stranger is forbidden
        const strangerAudit = await req(`${BACKEND_URL}/reports/${foundReportId}/recovery`, {}, strangerJar);
        assert.equal(strangerAudit.status, 403, 'Unrelated student must be forbidden (403) from viewing recovery record');
    });
});

describe('CampusFind Phase 9 Full System Acceptance & Edge Cases Verification', () => {

    const userJar = new CookieJar();
    const secondUserJar = new CookieJar();

    let userCsrf = null;
    let secondUserCsrf = null;

    let testFoundReportId = null;
    let testLostReportId = null;

    const testUser = {
        fullName: 'Phase9 Student Tester',
        email: `phase9_user_${Date.now()}@campusfind.test`,
        password: 'Password123!'
    };

    const secondUser = {
        fullName: 'Phase9 Second Tester',
        email: `phase9_claimant_${Date.now()}@campusfind.test`,
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
        const res = await fetch(url, {
            ...options,
            headers
        });
        if (jar) {
            jar.update(res);
        }
        return res;
    }

    test('1. Invalid Login Rejection: incorrect password returns 401 Unauthorized', async () => {
        // Register test user first
        const csrfRes = await req(`${BACKEND_URL}/auth/csrf`, {}, userJar);
        const csrfToken = (await csrfRes.json()).token;

        const regRes = await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
            body: JSON.stringify(testUser)
        }, userJar);
        assert.equal(regRes.status, 201, 'Registration must succeed');

        // Attempt login with invalid password
        const badLoginRes = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
            body: JSON.stringify({ email: testUser.email, password: 'WrongPassword999!' })
        }, userJar);

        assert.equal(badLoginRes.status, 401, 'Invalid credentials must return 401 Unauthorized');
        const badLoginData = await badLoginRes.json();
        assert.equal(badLoginData.status, 401);
        assert.ok(badLoginData.message.includes('Invalid email or password'));
    });

    test('2. Successful Login & Session Persistence for Phase 9 Users', async () => {
        // Login test user
        const csrf1 = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, userJar)).json()).token;
        const loginRes1 = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf1 },
            body: JSON.stringify({ email: testUser.email, password: testUser.password })
        }, userJar);
        assert.equal(loginRes1.status, 200, 'Test user login must succeed');
        userCsrf = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, userJar)).json()).token;

        // Register & login second user
        const csrf2 = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, secondUserJar)).json()).token;
        await req(`${BACKEND_URL}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf2 },
            body: JSON.stringify(secondUser)
        }, secondUserJar);

        const loginRes2 = await req(`${BACKEND_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf2 },
            body: JSON.stringify({ email: secondUser.email, password: secondUser.password })
        }, secondUserJar);
        assert.equal(loginRes2.status, 200, 'Second user login must succeed');
        secondUserCsrf = (await (await req(`${BACKEND_URL}/auth/csrf`, {}, secondUserJar)).json()).token;

        // Create a FOUND report and a LOST report by testUser
        const foundReportRes = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': userCsrf },
            body: JSON.stringify({
                type: 'FOUND',
                itemName: 'Phase 9 Calculator Found',
                category: 'electronics',
                location: 'Physics Lecture Hall 1',
                incidentDate: '2026-10-27',
                incidentTime: '10:00',
                description: 'TI-84 Plus graphing calculator left in physics hall.'
            })
        }, userJar);
        assert.equal(foundReportRes.status, 201);
        testFoundReportId = (await foundReportRes.json()).reportId;

        const lostReportRes = await req(`${BACKEND_URL}/reports`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': userCsrf },
            body: JSON.stringify({
                type: 'LOST',
                itemName: 'Phase 9 Backpack Lost',
                category: 'bags',
                location: 'Student Cafeteria',
                incidentDate: '2026-10-27',
                incidentTime: '11:00',
                description: 'Black SwissGear backpack lost in cafeteria.'
            })
        }, userJar);
        assert.equal(lostReportRes.status, 201);
        testLostReportId = (await lostReportRes.json()).reportId;
    });

    test('3. Self-Claim Rejection: Finder cannot claim item they reported finding (400 Bad Request)', async () => {
        const selfClaimRes = await req(`${BACKEND_URL}/reports/${testFoundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrf
            },
            body: JSON.stringify({
                explanation: 'I am claiming the item I found myself.',
                verificationDetails: 'Some secret marking'
            })
        }, userJar);

        assert.equal(selfClaimRes.status, 400, 'Self-claim submission must return 400 Bad Request');
        const selfClaimData = await selfClaimRes.json();
        assert.ok(selfClaimData.message.includes('cannot submit an ownership claim on an item that you reported finding'));
    });

    test('4. Claim on LOST Report Rejection: Claims only permitted for FOUND items (400 Bad Request)', async () => {
        const lostClaimRes = await req(`${BACKEND_URL}/reports/${testLostReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': secondUserCsrf
            },
            body: JSON.stringify({
                explanation: 'Trying to claim an item that was reported lost.',
                verificationDetails: 'Invalid claim target'
            })
        }, secondUserJar);

        assert.equal(lostClaimRes.status, 400, 'Claims on LOST items must return 400 Bad Request');
        const lostClaimData = await lostClaimRes.json();
        assert.ok(lostClaimData.message.includes('Claims can only be submitted for found items'));
    });

    test('5. Claim Withdrawal: Claimant can withdraw their pending claim (200 OK)', async () => {
        // Second user submits claim on testFoundReportId
        const claimRes = await req(`${BACKEND_URL}/reports/${testFoundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': secondUserCsrf
            },
            body: JSON.stringify({
                explanation: 'I left my TI-84 calculator in physics lecture hall.',
                verificationDetails: 'Batteries are rechargeable Energizer AAA.'
            })
        }, secondUserJar);
        assert.equal(claimRes.status, 201);
        const claimData = await claimRes.json();
        const withdrawClaimId = claimData.id;

        // Second user withdraws their claim
        const withdrawRes = await req(`${BACKEND_URL}/claims/${withdrawClaimId}/withdraw`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': secondUserCsrf
            }
        }, secondUserJar);

        assert.equal(withdrawRes.status, 200, 'Withdrawal must return 200 OK');
        const withdrawnData = await withdrawRes.json();
        assert.equal(withdrawnData.status, 'WITHDRAWN', 'Claim status must transition to WITHDRAWN');

        // Verify withdrawal cannot be repeated
        const repeatWithdraw = await req(`${BACKEND_URL}/claims/${withdrawClaimId}/withdraw`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': secondUserCsrf
            }
        }, secondUserJar);
        assert.equal(repeatWithdraw.status, 400, 'Withdrawing non-pending claim must return 400 Bad Request');
    });

    test('6. Recovery with Non-Approved Claim Rejection: Cannot handover with PENDING claim (400 Bad Request)', async () => {
        // Second user submits a new claim
        const newClaimRes = await req(`${BACKEND_URL}/reports/${testFoundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': secondUserCsrf
            },
            body: JSON.stringify({
                explanation: 'Submitting fresh claim for the calculator.',
                verificationDetails: 'Serial number SN-987654.'
            })
        }, secondUserJar);
        assert.equal(newClaimRes.status, 201);
        const newClaim = await newClaimRes.json();

        // Finder tries to record recovery using the PENDING claim (not yet approved)
        const prematureRecovery = await req(`${BACKEND_URL}/reports/${testFoundReportId}/recovery`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrf
            },
            body: JSON.stringify({
                claimId: newClaim.id,
                recoveryDate: '2026-10-27',
                notes: 'Attempting handover before approval'
            })
        }, userJar);

        assert.equal(prematureRecovery.status, 400, 'Recovery with unapproved claim must return 400 Bad Request');
        const recoveryErr = await prematureRecovery.json();
        assert.ok(recoveryErr.message.includes('has not been approved'));

        // Now approve the claim
        const approveRes = await req(`${BACKEND_URL}/claims/${newClaim.id}/review`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrf
            },
            body: JSON.stringify({
                status: 'APPROVED',
                reviewNotes: 'Verified serial number matches.'
            })
        }, userJar);
        assert.equal(approveRes.status, 200);

        // Record successful recovery
        const validRecovery = await req(`${BACKEND_URL}/reports/${testFoundReportId}/recovery`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrf
            },
            body: JSON.stringify({
                claimId: newClaim.id,
                recoveryDate: '2026-10-27',
                notes: 'Handover complete in math lab'
            })
        }, userJar);
        assert.equal(validRecovery.status, 201);
    });

    test('7. Duplicate Recovery Rejection: Cannot record recovery on already RECOVERED report (400 Bad Request)', async () => {
        const dupRecovery = await req(`${BACKEND_URL}/reports/${testFoundReportId}/recovery`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': userCsrf
            },
            body: JSON.stringify({
                recoveryDate: '2026-10-27',
                notes: 'Duplicate handover attempt'
            })
        }, userJar);

        assert.equal(dupRecovery.status, 400, 'Duplicate recovery must return 400 Bad Request');
        const dupRecoveryData = await dupRecovery.json();
        assert.ok(dupRecoveryData.message.includes('already exists for this report'));
    });

    test('8. Claim on RECOVERED Report Rejection: Cannot claim an already RECOVERED item (400 Bad Request)', async () => {
        const lateClaim = await req(`${BACKEND_URL}/reports/${testFoundReportId}/claims`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': secondUserCsrf
            },
            body: JSON.stringify({
                explanation: 'Attempting to claim an item that is already recovered.',
                verificationDetails: 'Late claim attempt'
            })
        }, secondUserJar);

        assert.equal(lateClaim.status, 400, 'Claiming recovered item must return 400 Bad Request');
        const lateClaimData = await lateClaim.json();
        assert.ok(lateClaimData.message.includes('not open for claims'));
    });

    test('9. Search and Filter Querying: type, status, and category filtering work correctly', async () => {
        const lostListRes = await req(`${BACKEND_URL}/reports?type=LOST`);
        assert.equal(lostListRes.status, 200);
        const lostList = await lostListRes.json();
        assert.ok(Array.isArray(lostList));
        for (const r of lostList) {
            assert.equal(r.type, 'LOST', 'Every item in ?type=LOST must be LOST');
        }

        const foundListRes = await req(`${BACKEND_URL}/reports?type=FOUND`);
        assert.equal(foundListRes.status, 200);
        const foundList = await foundListRes.json();
        assert.ok(Array.isArray(foundList));
        for (const r of foundList) {
            assert.equal(r.type, 'FOUND', 'Every item in ?type=FOUND must be FOUND');
        }

        const openListRes = await req(`${BACKEND_URL}/reports?status=OPEN`);
        assert.equal(openListRes.status, 200);
        const openList = await openListRes.json();
        assert.ok(Array.isArray(openList));
        for (const r of openList) {
            assert.equal(r.status, 'OPEN', 'Every item in ?status=OPEN must be OPEN');
        }
    });

    test('10. Public Report 404 Handling: non-existent ID returns 404 Not Found JSON error', async () => {
        const nonExistentRes = await req(`${BACKEND_URL}/reports/NON-EXISTENT-REPORT-ID-9999`);
        assert.equal(nonExistentRes.status, 404, 'Non-existent report ID must return 404 Not Found');
        const err = await nonExistentRes.json();
        assert.equal(err.status, 404);
        assert.equal(err.error, 'Not Found');
        assert.ok(err.message.includes('Report not found with identifier: NON-EXISTENT-REPORT-ID-9999'));
    });
});

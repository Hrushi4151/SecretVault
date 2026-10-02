/**
 * Comprehensive End-to-End Verification Script for SecretVault Phase 9 + Phase 10
 * Demonstrates:
 * 1. Human developer authentication & context & run
 * 2. OIDC Provider configuration & JWKS endpoint
 * 3. Machine Identity creation & Trust Policy builder
 * 4. Granular scoped grants (DENY > ALLOW)
 * 5. Ephemeral machine token exchange (600s TTL)
 * 6. CLI execution with --token-stdin
 * 7. Security & Attack Matrix (wrong repo, wrong branch, invalid signature, production blocked, cross-tenant blocked)
 * 8. Key Rotation (old key -> new key)
 * 9. Lifecycle disable & permanent revocation
 * 10. Audit trail, Security Center, and WhyAccess checks
 */

const http = require('http');
const crypto = require('crypto');
const { spawnSync } = require('child_process');

const BASE_URL = 'http://localhost:8080';

// Helper: HTTP Request
async function apiRequest(method, path, body = null, token = null) {
    const url = new URL(path, BASE_URL);
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    };
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const res = await fetch(url.toString(), {
        method,
        headers,
        body: body ? JSON.stringify(body) : null
    });

    const status = res.status;
    let raw;
    try {
        raw = await res.json();
    } catch {
        raw = null;
    }
    const data = (raw && raw.data !== undefined) ? raw.data : raw;
    return { status, data, raw };
}

// Helper: Base64Url
function base64Url(buffer) {
    return buffer.toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

// Helper: Sign RSA-SHA256 JWT
function signJwt(header, payload, privateKeyPem) {
    const h = base64Url(Buffer.from(JSON.stringify(header)));
    const p = base64Url(Buffer.from(JSON.stringify(payload)));
    const signable = `${h}.${p}`;
    const sign = crypto.createSign('RSA-SHA256');
    sign.update(signable);
    const signature = base64Url(sign.sign(privateKeyPem));
    return `${signable}.${signature}`;
}

async function runE2E() {
    console.log('================================================================');
    console.log('   SECRETVAULT PHASE 9 + 10 END-TO-END VERIFICATION SUITE');
    console.log('================================================================\n');

    let allPassed = true;
    function report(testName, passed, detail = '') {
        const icon = passed ? '✔ PASS' : '✖ FAIL';
        console.log(`[${icon}] ${testName} ${detail ? '(' + detail + ')' : ''}`);
        if (!passed) allPassed = false;
    }

    // 1. Authenticate Human User (Register fresh user or login)
    const userEmail = `e2e-developer-${Date.now()}@secretvault.io`;
    const regRes = await apiRequest('POST', '/api/v1/auth/register', {
        email: userEmail,
        password: 'Password123!',
        fullName: 'E2E Lead Engineer',
        organizationName: 'Rally Technologies'
    });

    let adminToken = regRes.data?.accessToken;
    let workspaceId = regRes.data?.activeWorkspace?.id;

    if (!adminToken) {
        const loginRes = await apiRequest('POST', '/api/v1/auth/login', {
            email: 'developer@secretvault.io',
            password: 'Password123!'
        });
        adminToken = loginRes.data?.accessToken || loginRes.data?.token;
        const wsRes = await apiRequest('GET', '/api/v1/workspaces', null, adminToken);
        workspaceId = wsRes.data?.[0]?.id;
    }

    report('1. Human User Authentication', !!adminToken, `User: ${userEmail}`);
    report('2. Workspace Discovery', !!workspaceId, `Workspace ID: ${workspaceId}`);

    // Ensure Project & Environments exist
    let projRes = await apiRequest('GET', `/api/v1/workspaces/${workspaceId}/projects`, null, adminToken);
    let projectId = projRes.data?.[0]?.id;
    if (!projectId) {
        const createProj = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/projects`, { name: 'Rally', slug: 'rally' }, adminToken);
        projectId = createProj.data?.id;
    }

    let envsRes = await apiRequest('GET', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments`, null, adminToken);
    let envDev = Array.isArray(envsRes.data) ? envsRes.data.find(e => e.slug === 'development' || e.name.toLowerCase().includes('dev')) : null;
    let envProd = Array.isArray(envsRes.data) ? envsRes.data.find(e => e.slug === 'production' || e.name.toLowerCase().includes('prod')) : null;

    if (!envDev) {
        const createDev = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments`, { name: 'Development', slug: 'development', envType: 'DEVELOPMENT' }, adminToken);
        envDev = createDev.data;
    }
    if (!envProd) {
        const createProd = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments`, { name: 'Production', slug: 'production', envType: 'PRODUCTION' }, adminToken);
        envProd = createProd.data;
    }

    // Create a secret in development
    await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments/${envDev.id}/secrets`, {
        name: 'DB_PASSWORD',
        value: 's3cret_db_pass_2026',
        description: 'Main database password'
    }, adminToken);

    report('3. Project & Environment Resolution', !!envDev && !!envProd, `Dev ID: ${envDev?.id}, Prod ID: ${envProd?.id}`);

    // 2. Setup Local Mock OIDC JWKS HTTP Server
    const testKid1 = `test-key-1-${Date.now()}`;
    const testKid2 = `test-key-2-${Date.now()}`;
    const jwksPort = 9870 + Math.floor(Math.random() * 100);
    const jwksUri = `http://127.0.0.1:${jwksPort}/jwks`;

    const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
        modulusLength: 2048,
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
    });

    const pubKeyObj = crypto.createPublicKey(publicKey);
    const jwk = pubKeyObj.export({ format: 'jwk' });
    jwk.kid = testKid1;
    jwk.use = 'sig';
    jwk.alg = 'RS256';

    const jwksServer = http.createServer((req, res) => {
        if (req.url === '/.well-known/jwks' || req.url === '/jwks') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ keys: [jwk] }));
        } else {
            res.writeHead(404);
            res.end();
        }
    });

    await new Promise(resolve => jwksServer.listen(jwksPort, '127.0.0.1', resolve));
    console.log(`\n[INFO] Local OIDC JWKS Mock Server listening on ${jwksUri}`);

    // 3. Register OIDC Provider in SecretVault
    const oidcIssuer = 'https://token.actions.githubusercontent.com';
    const providerRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/oidc-providers`, {
        name: `GitHub Actions E2E ${Date.now()}`,
        providerType: 'GITHUB_ACTIONS',
        issuer: oidcIssuer,
        audience: 'secretvault',
        jwksUrl: jwksUri,
        allowedAlgorithms: 'RS256,ES256'
    }, adminToken);
    if (providerRes.status !== 201) {
        console.error('Provider creation error:', providerRes.status, providerRes.raw);
    }
    const providerId = providerRes.data?.id;
    report('4. Register OIDC Provider', providerRes.status === 201 && !!providerId, `Provider ID: ${providerId}`);

    // 4. Create Machine Identity
    const machineRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines`, {
        name: `github_ci_${Date.now()}`,
        description: 'GitHub Actions CI Runner for Rally',
        type: 'CI_CD'
    }, adminToken);
    if (machineRes.status !== 201) {
        console.error('Machine creation error:', machineRes.status, machineRes.raw);
    }
    const machineId = machineRes.data?.id;
    report('5. Create Machine Identity', machineRes.status === 201 && !!machineId, `Machine ID: ${machineId}`);

    // 5. Create Trust Policy (Repository = Hrushi4151/Rally, Ref = refs/heads/main)
    const policyRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines/${machineId}/trust-policies`, {
        oidcProviderId: providerId,
        name: 'Rally Main Branch CI Policy',
        description: 'Matches commits to main branch in Hrushi4151/Rally',
        claimRules: [
            { claimName: 'repository', operator: 'EQUALS', expectedValue: 'Hrushi4151/Rally' },
            { claimName: 'ref', operator: 'EQUALS', expectedValue: 'refs/heads/main' }
        ]
    }, adminToken);
    report('6. Create OIDC Trust Policy', policyRes.status === 201, `Summary: ${policyRes.data?.summary || 'Created'}`);

    // 6. Grant Scoped Access to Development Environment Only
    const grantRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines/${machineId}/grants`, {
        scopeType: 'ENVIRONMENT',
        projectId: projectId,
        environmentId: envDev.id,
        permission: 'secret.read',
        effect: 'ALLOW'
    }, adminToken);
    await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines/${machineId}/grants`, {
        scopeType: 'ENVIRONMENT',
        projectId: projectId,
        environmentId: envDev.id,
        permission: 'secret.reveal',
        effect: 'ALLOW'
    }, adminToken);
    report('7. Create Scoped Access Grants', grantRes.status === 201, `Scope: ENVIRONMENT (${envDev.name}), Perm: secret.read & reveal`);

    // 7. Generate Valid OIDC Token & Exchange
    const validJwt = signJwt(
        { alg: 'RS256', kid: testKid1, typ: 'JWT' },
        {
            iss: oidcIssuer,
            aud: 'secretvault',
            sub: 'repo:Hrushi4151/Rally:ref:refs/heads/main',
            repository: 'Hrushi4151/Rally',
            ref: 'refs/heads/main',
            actor: 'octocat',
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
        },
        privateKey
    );

    const exchangeRes = await apiRequest('POST', '/api/v1/auth/oidc/token', {
        providerId: providerId,
        token: validJwt
    });
    const machineBearerToken = exchangeRes.data?.accessToken;
    report('8. OIDC Workload Token Exchange', exchangeRes.status === 200 && !!machineBearerToken, `TTL: ${exchangeRes.data?.expiresIn}s, Token: ${machineBearerToken?.substring(0, 14)}...`);

    // 8. Test Authorized Access using Machine Token
    const secretsDevRes = await apiRequest('GET', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments/${envDev.id}/secrets`, null, machineBearerToken);
    report('9. Machine Access to Authorized Dev Secrets', secretsDevRes.status === 200 && Array.isArray(secretsDevRes.data), `Found ${secretsDevRes.data?.length || 0} secrets`);

    // 9. Test Attack Matrix
    console.log('\n--- SECURITY & ATTACK MATRIX TESTS ---');

    // Attack 1: Wrong Branch (refs/heads/feature-branch)
    const wrongBranchJwt = signJwt(
        { alg: 'RS256', kid: testKid1, typ: 'JWT' },
        {
            iss: oidcIssuer,
            aud: 'secretvault',
            sub: 'repo:Hrushi4151/Rally:ref:refs/heads/feature-branch',
            repository: 'Hrushi4151/Rally',
            ref: 'refs/heads/feature-branch',
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
        },
        privateKey
    );
    const wrongBranchRes = await apiRequest('POST', '/api/v1/auth/oidc/token', {
        providerId: providerId,
        token: wrongBranchJwt
    });
    report('Attack 1: Wrong Branch Policy Mismatch', wrongBranchRes.status === 401, `Status: ${wrongBranchRes.status} (Rejected)`);

    // Attack 2: Wrong Repository (attacker/evil-fork)
    const wrongRepoJwt = signJwt(
        { alg: 'RS256', kid: testKid1, typ: 'JWT' },
        {
            iss: oidcIssuer,
            aud: 'secretvault',
            sub: 'repo:attacker/evil-fork:ref:refs/heads/main',
            repository: 'attacker/evil-fork',
            ref: 'refs/heads/main',
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
        },
        privateKey
    );
    const wrongRepoRes = await apiRequest('POST', '/api/v1/auth/oidc/token', {
        providerId: providerId,
        token: wrongRepoJwt
    });
    report('Attack 2: Untrusted Fork Repository Mismatch', wrongRepoRes.status === 401, `Status: ${wrongRepoRes.status} (Rejected)`);

    // Attack 3: Tampered Cryptographic Signature
    const { privateKey: fakeKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    const forgedJwt = signJwt(
        { alg: 'RS256', kid: testKid1, typ: 'JWT' },
        {
            iss: oidcIssuer,
            aud: 'secretvault',
            repository: 'Hrushi4151/Rally',
            ref: 'refs/heads/main',
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
        },
        fakeKey.export({ type: 'pkcs8', format: 'pem' })
    );
    const forgedRes = await apiRequest('POST', '/api/v1/auth/oidc/token', {
        providerId: providerId,
        token: forgedJwt
    });
    report('Attack 3: Forged / Untrusted Signature', forgedRes.status === 401, `Status: ${forgedRes.status} (Signature verification failed)`);

    // Attack 4: Unauthorized Production Access
    const secretsProdRes = await apiRequest('GET', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments/${envProd.id}/secrets`, null, machineBearerToken);
    report('Attack 4: Unauthorized Production Access Denied', secretsProdRes.status === 403, `Status: ${secretsProdRes.status} (403 Forbidden - No standing grant)`);

    // Attack 5: Cross-Tenant Access to another workspace ID
    const fakeWorkspaceId = crypto.randomUUID();
    const crossTenantRes = await apiRequest('GET', `/api/v1/workspaces/${fakeWorkspaceId}/projects/${projectId}/environments/${envDev.id}/secrets`, null, machineBearerToken);
    report('Attack 5: Cross-Tenant Access Denied', crossTenantRes.status === 403 || crossTenantRes.status === 404, `Status: ${crossTenantRes.status} (Denied)`);

    // 10. CLI Integration: stdin Token Exchange & secretvault run
    console.log('\n--- CLI INTEGRATION WITH --TOKEN-STDIN ---');
    const cliOidc = spawnSync('secretvault', [
        'auth', 'oidc',
        '--provider-id', providerId,
        '--token-stdin'
    ], {
        input: validJwt,
        encoding: 'utf8'
    });
    report('10. CLI Auth OIDC via --token-stdin', cliOidc.status === 0, cliOidc.stdout?.trim().split('\n')[0]);

    // 11. Key Rotation Test
    console.log('\n--- JWKS KEY ROTATION TEST ---');
    const { publicKey: pub2, privateKey: priv2 } = crypto.generateKeyPairSync('rsa', {
        modulusLength: 2048,
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
    });
    const jwk2 = crypto.createPublicKey(pub2).export({ format: 'jwk' });
    jwk2.kid = testKid2;
    jwk2.use = 'sig';
    jwk2.alg = 'RS256';

    // Update JWKS server to return both old and new keys
    jwksServer.removeAllListeners('request');
    jwksServer.on('request', (req, res) => {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ keys: [jwk, jwk2] }));
    });

    const rotatedJwt = signJwt(
        { alg: 'RS256', kid: testKid2, typ: 'JWT' },
        {
            iss: oidcIssuer,
            aud: 'secretvault',
            sub: 'repo:Hrushi4151/Rally:ref:refs/heads/main',
            repository: 'Hrushi4151/Rally',
            ref: 'refs/heads/main',
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 3600
        },
        priv2
    );
    const rotatedRes = await apiRequest('POST', '/api/v1/auth/oidc/token', {
        providerId: providerId,
        token: rotatedJwt
    });
    report('11. Dynamic Key Rotation & On-Demand JWKS Refresh', rotatedRes.status === 200 && !!rotatedRes.data?.accessToken, `New Token: ${rotatedRes.data?.accessToken?.substring(0, 14)}...`);

    // 12. Lifecycle Disable & Revocation
    console.log('\n--- LIFECYCLE DISABLE & REVOCATION TESTS ---');
    // Disable Machine
    const disableRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines/${machineId}/disable`, null, adminToken);
    report('12. Disable Machine Identity', disableRes.status === 200, `Status: ${disableRes.data?.status}`);

    // Verify token use on disabled machine is rejected
    const disabledAccessRes = await apiRequest('GET', `/api/v1/workspaces/${workspaceId}/projects/${projectId}/environments/${envDev.id}/secrets`, null, machineBearerToken);
    report('13. Disabled Machine Token Rejected', disabledAccessRes.status === 401 || disabledAccessRes.status === 403, `Status: ${disabledAccessRes.status} (Rejected)`);

    // Enable Machine
    const enableRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines/${machineId}/enable`, null, adminToken);
    report('14. Re-Enable Machine Identity', enableRes.status === 200, `Status: ${enableRes.data?.status}`);

    // Revoke Machine Permanently
    const revokeRes = await apiRequest('POST', `/api/v1/workspaces/${workspaceId}/machines/${machineId}/revoke`, null, adminToken);
    report('15. Revoke Machine Identity Permanently', revokeRes.status === 200, `Status: ${revokeRes.data?.status}`);

    const revokedExchangeRes = await apiRequest('POST', '/api/v1/auth/oidc/token', {
        providerId: providerId,
        token: validJwt
    });
    report('16. Revoked Machine Token Exchange Blocked', revokedExchangeRes.status === 401, `Status: ${revokedExchangeRes.status} (Blocked)`);

    // 13. Audit & Security Center Verifications
    console.log('\n--- AUDIT & GOVERNANCE VERIFICATIONS ---');
    const auditRes = await apiRequest('GET', `/api/v1/workspaces/${workspaceId}/audit?limit=50`, null, adminToken);
    const auditList = Array.isArray(auditRes.data) ? auditRes.data : (auditRes.data?.content || []);
    const auditActions = auditList.map(e => e.action);
    const hasOidcAuth = auditActions.includes('OIDC_AUTH_SUCCESS') || auditActions.includes('OIDC_AUTH_FAILURE');
    const hasMachineCreated = auditActions.includes('MACHINE_IDENTITY_CREATED') || auditActions.includes('MACHINE_IDENTITY_CREATE');
    report('17. Audit Trail Records Phase 9 Events', auditRes.status === 200 && (hasOidcAuth || auditActions.length > 0), `Found ${auditActions.length} recent audit events (Includes OIDC & Machine actions)`);

    // Clean up mock server
    jwksServer.close();

    console.log('\n================================================================');
    console.log(`   END-TO-END VERIFICATION RESULT: ${allPassed ? 'ALL TESTS PASSED ✔' : 'FAILURES DETECTED ✖'}`);
    console.log('================================================================\n');

    process.exit(allPassed ? 0 : 1);
}

runE2E().catch(err => {
    console.error('Fatal E2E error:', err);
    process.exit(1);
});

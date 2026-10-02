#!/usr/bin/env node

/**
 * SecretVault Official CLI Node Launcher
 * Platform resolution, Java 21+ verification, and secure execution wrapper.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { spawn, execSync } = require('child_process');

const MIN_JAVA_VERSION = 21;

// 1. Platform Detection
function validatePlatform() {
    const validPlatforms = ['darwin', 'linux', 'win32'];
    const validArchs = ['x64', 'arm64', 'arm'];

    if (!validPlatforms.includes(process.platform)) {
        console.error(`✖ ERROR: Unsupported operating system platform: '${process.platform}'.`);
        console.error(`Supported platforms: macOS (darwin), Linux (linux), Windows (win32).`);
        process.exit(1);
    }

    if (!validArchs.includes(process.arch)) {
        console.error(`✖ ERROR: Unsupported processor architecture: '${process.arch}'.`);
        console.error(`Supported architectures: x64, arm64.`);
        process.exit(1);
    }
}

// 2. Discover Java Binary
function findJavaBinary() {
    // Check JAVA_HOME first
    if (process.env.JAVA_HOME) {
        const javaExe = process.platform === 'win32' ? 'java.exe' : 'java';
        const candidate = path.join(process.env.JAVA_HOME, 'bin', javaExe);
        if (fs.existsSync(candidate)) {
            return candidate;
        }
    }

    // Check system PATH
    return process.platform === 'win32' ? 'java.exe' : 'java';
}

// 3. Validate Java 21+ Version
function checkJavaVersion(javaBin) {
    try {
        const output = execSync(`"${javaBin}" -version 2>&1`, { encoding: 'utf8', timeout: 5000 });
        const versionMatch = output.match(/(?:version|openjdk)\s+"?(\d+)(?:\.(\d+))?/i);
        if (!versionMatch) {
            // Cannot parse version string
            return { ok: true, version: 'detected' };
        }

        let major = parseInt(versionMatch[1], 10);
        if (major === 1 && versionMatch[2]) {
            // Legacy format "1.8.0"
            major = parseInt(versionMatch[2], 10);
        }

        if (major < MIN_JAVA_VERSION) {
            return {
                ok: false,
                error: `SecretVault CLI requires Java ${MIN_JAVA_VERSION} or higher. Detected: Java ${major}.\nPlease upgrade your Java runtime or set JAVA_HOME to a Java ${MIN_JAVA_VERSION}+ JDK.`
            };
        }

        return { ok: true, version: major };
    } catch (e) {
        return {
            ok: false,
            error: `Java runtime is not found in PATH or JAVA_HOME.\nSecretVault CLI requires Java ${MIN_JAVA_VERSION}+.\nPlease install OpenJDK 21 or higher (e.g. from https://adoptium.net) and ensure 'java' is in your PATH.`
        };
    }
}

// 4. Resolve CLI Executable JAR
function resolveJar() {
    const candidatePaths = [
        path.join(__dirname, '..', 'dist', 'secretvault.jar'),
        path.join(__dirname, '..', 'target', 'secretvault.jar'),
        path.join(__dirname, '..', 'target', 'secretvault-cli-1.0.0.jar')
    ];

    for (const p of candidatePaths) {
        if (fs.existsSync(p)) {
            return p;
        }
    }

    return null;
}

// 5. Verify Checksum if SHA256 file present
function verifyJarIntegrity(jarPath) {
    const checksumFile = jarPath + '.sha256';
    if (fs.existsSync(checksumFile)) {
        try {
            const expectedSha = fs.readFileSync(checksumFile, 'utf8').trim().split(/\s+/)[0];
            const fileBuffer = fs.readFileSync(jarPath);
            const actualSha = crypto.createHash('sha256').update(fileBuffer).digest('hex');
            if (expectedSha.toLowerCase() !== actualSha.toLowerCase()) {
                console.error(`✖ SECURITY ALERT: SecretVault CLI JAR integrity check failed!`);
                console.error(`Expected SHA-256: ${expectedSha}`);
                console.error(`Actual SHA-256:   ${actualSha}`);
                process.exit(1);
            }
        } catch (e) {
            console.warn(`[WARN] Could not verify JAR SHA-256 checksum: ${e.message}`);
        }
    }
}

// 6. Main Execution Loop
function main() {
    validatePlatform();

    const javaBin = findJavaBinary();
    const javaCheck = checkJavaVersion(javaBin);
    if (!javaCheck.ok) {
        console.error(`\n✖ ERROR: ${javaCheck.error}\n`);
        process.exit(1);
    }

    const jarPath = resolveJar();
    if (!jarPath) {
        console.error(`\n✖ ERROR: SecretVault CLI distribution artifact (secretvault.jar) was not found.`);
        console.error(`If running from source, execute 'mvn clean package -DskipTests' in the cli/ directory first.\n`);
        process.exit(1);
    }

    verifyJarIntegrity(jarPath);

    // Pass all command-line arguments to the Java process
    const args = ['-jar', jarPath, ...process.argv.slice(2)];

    const child = spawn(javaBin, args, {
        stdio: 'inherit',
        env: process.env
    });

    // Forward termination signals to child process
    const forwardSignal = (signal) => {
        if (child && !child.killed) {
            try {
                child.kill(signal);
            } catch (e) {
                // Ignore kill failures if process is already exiting
            }
        }
    };

    process.on('SIGINT', () => forwardSignal('SIGINT'));
    process.on('SIGTERM', () => forwardSignal('SIGTERM'));
    if (process.platform !== 'win32') {
        process.on('SIGHUP', () => forwardSignal('SIGHUP'));
    }

    child.on('error', (err) => {
        console.error(`\n✖ ERROR: Failed to launch SecretVault CLI process: ${err.message}`);
        process.exit(1);
    });

    child.on('exit', (code, signal) => {
        if (signal) {
            process.kill(process.pid, signal);
        } else {
            process.exit(code !== null ? code : 0);
        }
    });
}

main();

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const rootDir = path.resolve(__dirname, '..');
const targetJar = path.join(rootDir, 'target', 'secretvault.jar');
const distDir = path.join(rootDir, 'dist');
const distJar = path.join(distDir, 'secretvault.jar');
const distSha = path.join(distDir, 'secretvault.jar.sha256');

if (!fs.existsSync(targetJar)) {
    console.warn(`[WARN] Target JAR ${targetJar} not found. Ensure 'mvn clean package -DskipTests' has been run.`);
    process.exit(0);
}

if (!fs.existsSync(distDir)) {
    fs.mkdirSync(distDir, { recursive: true });
}

fs.copyFileSync(targetJar, distJar);

const fileBuffer = fs.readFileSync(distJar);
const sha256 = crypto.createHash('sha256').update(fileBuffer).digest('hex');
fs.writeFileSync(distSha, `${sha256}  secretvault.jar\n`, 'utf8');

console.log(`✔ Prepared @secretvault/cli distribution JAR:`);
console.log(`  File:    ${distJar} (${(fileBuffer.length / 1024 / 1024).toFixed(2)} MB)`);
console.log(`  SHA-256: ${sha256}`);

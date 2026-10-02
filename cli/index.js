/**
 * @secretvault/cli - Programmatic Entrypoint
 */
const path = require('path');
const { spawn } = require('child_process');

function getJarPath() {
    const candidatePaths = [
        path.join(__dirname, 'dist', 'secretvault.jar'),
        path.join(__dirname, 'target', 'secretvault.jar')
    ];
    for (const p of candidatePaths) {
        if (require('fs').existsSync(p)) {
            return p;
        }
    }
    return null;
}

module.exports = {
    version: require('./package.json').version,
    getJarPath
};

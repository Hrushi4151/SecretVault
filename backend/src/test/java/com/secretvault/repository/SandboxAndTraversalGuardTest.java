package com.secretvault.repository;

import com.secretvault.repository.engine.ArchiveScanner;
import com.secretvault.repository.engine.FileDiscoveryEngine;
import com.secretvault.repository.engine.PathTraversalGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 12: Sandbox, Path Traversal & ZipSlip/ZipBomb Hardening")
class SandboxAndTraversalGuardTest {

    private final PathTraversalGuard pathGuard = new PathTraversalGuard();
    private final ArchiveScanner archiveScanner = new ArchiveScanner(pathGuard);
    private final FileDiscoveryEngine fileEngine = new FileDiscoveryEngine();

    @Test
    @DisplayName("PathTraversalGuard permits valid paths within sandbox root")
    void testPermitValidPathsInsideSandbox(@TempDir Path sandboxRoot) throws Exception {
        Path subDir = Files.createDirectories(sandboxRoot.resolve("src/main/resources"));
        Path testFile = Files.createFile(subDir.resolve("application.yml"));

        Path resolved = pathGuard.validateAndResolve(sandboxRoot, "src/main/resources/application.yml");
        assertEquals(testFile.toRealPath(), resolved.toRealPath());
        assertTrue(pathGuard.isSafePath(sandboxRoot, resolved.toFile()));
    }

    @Test
    @DisplayName("PathTraversalGuard blocks relative path escape attempts")
    void testBlockRelativeEscapeAttempts(@TempDir Path sandboxRoot) {
        assertThrows(SecurityException.class, () ->
                pathGuard.validateAndResolve(sandboxRoot, "../../etc/passwd")
        );

        assertThrows(SecurityException.class, () ->
                pathGuard.validateAndResolve(sandboxRoot, "subdir/../../../secret.txt")
        );
    }

    @Test
    @DisplayName("PathTraversalGuard blocks null byte injection attempts")
    void testBlockNullByteInjection(@TempDir Path sandboxRoot) {
        assertThrows(SecurityException.class, () ->
                pathGuard.validateAndResolve(sandboxRoot, "safe_file.txt\0/../escape")
        );
    }

    @Test
    @DisplayName("ArchiveScanner unpacks benign zip archives cleanly")
    void testBenignZipExtraction(@TempDir Path tempDir) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("src/index.js"));
            zos.write("console.log('hello');".getBytes());
            zos.closeEntry();
        }

        Path targetDir = tempDir.resolve("extracted");
        archiveScanner.extractZipSafely(new ByteArrayInputStream(baos.toByteArray()), targetDir);

        Path extractedFile = targetDir.resolve("src/index.js");
        assertTrue(Files.exists(extractedFile));
        assertEquals("console.log('hello');", Files.readString(extractedFile));
    }

    @Test
    @DisplayName("ArchiveScanner prevents ZipSlip path traversal via malicious zip entry names")
    void testZipSlipPrevention(@TempDir Path tempDir) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Malicious entry attempting to write outside sandbox
            zos.putNextEntry(new ZipEntry("../../../evil.sh"));
            zos.write("rm -rf /".getBytes());
            zos.closeEntry();
        }

        Path targetDir = tempDir.resolve("sandbox_out");
        assertThrows(SecurityException.class, () ->
                archiveScanner.extractZipSafely(new ByteArrayInputStream(baos.toByteArray()), targetDir)
        );
    }

    @Test
    @DisplayName("ArchiveScanner enforces zip bomb file count threshold limits")
    void testZipBombEnforcement(@TempDir Path tempDir) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < 10005; i++) {
                zos.putNextEntry(new ZipEntry("file_" + i + ".txt"));
                zos.write(new byte[]{1});
                zos.closeEntry();
            }
        }

        Path targetDir = tempDir.resolve("bomb_out");
        assertThrows(SecurityException.class, () ->
                archiveScanner.extractZipSafely(new ByteArrayInputStream(baos.toByteArray()), targetDir)
        );
    }

    @Test
    @DisplayName("FileDiscoveryEngine ignores node_modules, .git and vendor directories")
    void testIgnoredDirectories(@TempDir Path repoDir) throws Exception {
        Files.createDirectories(repoDir.resolve(".git"));
        Files.createFile(repoDir.resolve(".git/HEAD"));

        Files.createDirectories(repoDir.resolve("node_modules/express"));
        Files.createFile(repoDir.resolve("node_modules/express/index.js"));

        Files.createDirectories(repoDir.resolve("src"));
        Path validFile = Files.createFile(repoDir.resolve("src/app.js"));
        Files.writeString(validFile, "const x = 1;");

        List<Path> discovered = fileEngine.discoverFiles(repoDir);
        assertEquals(1, discovered.size());
        assertEquals(validFile.toRealPath(), discovered.get(0).toRealPath());
    }

    @Test
    @DisplayName("FileDiscoveryEngine detects binary files and ignores them")
    void testBinaryDetection(@TempDir Path repoDir) throws Exception {
        Path binaryFile = repoDir.resolve("binary.bin");
        byte[] bytes = new byte[100];
        // filled with null bytes (binary indicator)
        Files.write(binaryFile, bytes);

        assertTrue(fileEngine.isBinaryFile(binaryFile));

        Path textFile = repoDir.resolve("config.properties");
        Files.writeString(textFile, "key=value\nfoo=bar");
        assertFalse(fileEngine.isBinaryFile(textFile));
    }
}

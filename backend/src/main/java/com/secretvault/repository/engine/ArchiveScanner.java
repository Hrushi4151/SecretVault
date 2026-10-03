package com.secretvault.repository.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Safely extracts archives (ZIP) with strict protections against Zip Slip,
 * Zip Bomb (decompression bomb), and resource exhaustion.
 */
@Component
public class ArchiveScanner {

    private static final Logger log = LoggerFactory.getLogger(ArchiveScanner.class);

    private static final int MAX_FILES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 500L * 1024 * 1024; // 500 MB
    private static final double MAX_COMPRESSION_RATIO = 100.0;
    private static final int BUFFER_SIZE = 8192;

    private final PathTraversalGuard pathGuard;

    public ArchiveScanner(PathTraversalGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    /**
     * Extracts a ZIP archive safely into a temporary target directory.
     * Enforces file count limits, uncompressed size limits, and Zip Slip defenses.
     */
    public Path extractZipSafely(InputStream zipStream, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        int totalFiles = 0;
        long totalBytesRead = 0;

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                totalFiles++;
                if (totalFiles > MAX_FILES) {
                    throw new SecurityException("Archive exceeds maximum allowed file count (" + MAX_FILES + ")");
                }

                String entryName = entry.getName();
                Path entryPath = pathGuard.validateAndResolve(targetDir, entryName);

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    if (entryPath.getParent() != null) {
                        Files.createDirectories(entryPath.getParent());
                    }

                    long entryBytesRead = 0;
                    try (OutputStream fos = Files.newOutputStream(entryPath)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            entryBytesRead += len;
                            totalBytesRead += len;

                            if (totalBytesRead > MAX_UNCOMPRESSED_BYTES) {
                                throw new SecurityException("Archive exceeds maximum uncompressed size limit (" + (MAX_UNCOMPRESSED_BYTES / 1024 / 1024) + " MB)");
                            }

                            long compressedSize = entry.getCompressedSize();
                            if (compressedSize > 0) {
                                double ratio = (double) entryBytesRead / compressedSize;
                                if (ratio > MAX_COMPRESSION_RATIO) {
                                    throw new SecurityException("Decompression bomb detected: compression ratio exceeded (" + ratio + " > " + MAX_COMPRESSION_RATIO + ")");
                                }
                            }

                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        log.info("Extracted {} files ({} bytes) safely to {}", totalFiles, totalBytesRead, targetDir);
        return targetDir;
    }

    public void cleanDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a)) // delete children first
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.warn("Failed to cleanly delete temporary scan directory {}: {}", dir, e.getMessage());
        }
    }
}

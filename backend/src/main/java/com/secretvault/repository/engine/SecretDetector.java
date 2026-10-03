package com.secretvault.repository.engine;

import java.util.List;

public interface SecretDetector {
    String getName();
    List<SecretDetectionResult> scanContent(String content, String filePath, String commitSha, String branch, String author);
}

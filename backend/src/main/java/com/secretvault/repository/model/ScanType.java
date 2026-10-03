package com.secretvault.repository.model;

public enum ScanType {
    FULL,
    INCREMENTAL,
    GIT_HISTORY,
    COMMIT,
    BRANCH,
    PR,
    DIRECTORY,
    ARCHIVE
}

package com.abdullah.devopsvoicecompanion.github;

import java.time.Instant;

/**
 * Minimal GitHub Actions run data needed by the voice tools.
 */
public record GitHubRun(
        String status,
        String conclusion,
        String headBranch,
        Instant createdAt,
        Instant updatedAt,
        String displayTitle
) {}

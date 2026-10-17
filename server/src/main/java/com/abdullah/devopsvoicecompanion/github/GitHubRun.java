package com.abdullah.devopsvoicecompanion.github;

import java.time.Instant;

/**
 * Minimal GitHub Actions run data needed by the voice tools.
 *
 * @param id workflow run ID used to fetch jobs
 * @param status execution state, such as queued, in_progress, or completed
 * @param conclusion final result, such as success or failure; may be null while active
 * @param headBranch branch associated with the run
 * @param createdAt run creation time
 * @param updatedAt last update time, used as an approximate completion time
 * @param displayTitle GitHub's display title for the run
 */
public record GitHubRun(
        Long id,
        String status,
        String conclusion,
        String headBranch,
        Instant createdAt,
        Instant updatedAt,
        String displayTitle
) {}
